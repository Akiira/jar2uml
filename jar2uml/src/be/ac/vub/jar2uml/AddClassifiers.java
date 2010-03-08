/*******************************************************************************
 * Copyright (c) 2007-2010 Dennis Wagelaar, Vrije Universiteit Brussel.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Dennis Wagelaar, Vrije Universiteit Brussel
 *******************************************************************************/
package be.ac.vub.jar2uml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.Assert;

import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionList;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Interface;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.UMLPackage;

/**
 * Adds {@link Classifier}s to the UML {@link Model}. This represents the first pass in {@link JarToUML}.
 * @author Dennis Wagelaar <dennis.wagelaar@vub.ac.be>
 */
public class AddClassifiers extends AddToModel {

	/**
	 * Adds a tag to indicate element has been parsed
	 * from a classpath entry.
	 * @param element
	 */
	public static final void addClasspathTag(final Element element) {
		JarToUML.annotate(element, "classpath", "true"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	protected FixClassifierSwitch fixClassifier = new FixClassifierSwitch();
	protected ReplaceByClassifierSwitch replaceByClassifier = new ReplaceByClassifierSwitch();
	protected AddClassifierInterfaceSwitch addClassifierInterface = new AddClassifierInterfaceSwitch();
	protected AddInstructionReferencesVisitor addInstructionReferences = new AddInstructionReferencesVisitor(typeToClassifier);

	/**
	 * Creates a new {@link AddClassifiers}
	 * @param filter A filter to apply to model operations.
	 * @param monitor A progress monitor to check for end user cancellation.
	 * @param model The UML model to store generated elements in.
	 * @param includeFeatures Whether to include fields and methods.
	 * @param includeInstructionReferences Whether or not to include Java elements that are
	 * referred to by bytecode instructions.
	 */
	public AddClassifiers(Filter filter, IProgressMonitor monitor, Model model, 
			boolean includeFeatures, boolean includeInstructionReferences) {
		super(filter, monitor, model, includeFeatures, includeInstructionReferences);
	}

	/**
	 * Adds all classifiers in parsedClasses to the UML model. Does not add classifier properties.
	 * @param parsedClasses
	 * @throws IOException
	 * @throws JarToUMLException 
	 */
	public void addAllClassifiers(Collection<JavaClass> parsedClasses) throws IOException, JarToUMLException {
		for (JavaClass javaClass : parsedClasses) {
			addClassifier(javaClass, false);
			checkCancelled();
		}
	}

	/**
	 * Adds the closure of all referenced classifiers in parsedClasses to the UML model. Does not add classifier properties.
	 * @param parsedClasses
	 * @return The entries in parsedClasses that have not been added.
	 * @throws IOException
	 * @throws JarToUMLException 
	 */
	public List<JavaClass> addClassifiersClosure(Collection<JavaClass> parsedClasses) throws IOException, JarToUMLException {
		final List<JavaClass> processClasses = new ArrayList<JavaClass>(parsedClasses);
		final Set<JavaClass> addedClasses = new HashSet<JavaClass>();
		do {
			processClasses.removeAll(addedClasses);
			addedClasses.clear();
			for (JavaClass javaClass : processClasses) {
				if (addClassifier(javaClass, true)) {
					addedClasses.add(javaClass);
				}
				checkCancelled();
			}
		} while (!addedClasses.isEmpty());
		return processClasses;
	}

	/**
	 * Adds a classifier to the UML model that represents javaClass. Does not add classifier properties.
	 * @param javaClass The BCEL class representation to convert.
	 * @param isCp whether to treat javaClass as a classpath class.
	 * @return <code>true</code> iff javaClass was added.
	 * @throws JarToUMLException 
	 */
	public boolean addClassifier(JavaClass javaClass, boolean isCp) throws JarToUMLException {
		final String className = javaClass.getClassName();
		if (!filter(javaClass)) {
			logSkippedFiltered(javaClass);
			return false;
		}
		JarToUML.logger.finest(className);
		Classifier classifier;
		if (isCp) {
			classifier = findContainedClassifier.findClassifier(
					getModel(), className, null);
			if (classifier == null) {
				// classifier was not referenced
				return false;
			}
		} else {
			classifier = findContainedClassifier.findClassifier(
					getModel(), className, javaClass.isInterface() ? 
							UMLPackage.eINSTANCE.getInterface() : UMLPackage.eINSTANCE.getClass_());
		}
		Assert.assertNotNull(classifier);
		// replace by instance of correct meta-class, if necessary
		fixClassifier.setJavaClass(javaClass);
		classifier = fixClassifier.doSwitch(classifier);
		// add tag to classpath classifiers
		if (isCp) {
			addClasspathTag(classifier);
		}
		// add realizations/generalizations with correct types in 1st pass, as replacing already referenced types is not possible
		addReferencedInterfaces(javaClass);
		addReferencedGenerals(javaClass);
		// add realizations/generalizations in 1st pass, as class hierarchy is needed in 2nd pass
		addInterfaceRealizations(classifier, javaClass);
		addGeneralizations(classifier, javaClass);
		// add referred types in 1st pass, as replacing referenced types in the 2nd pass is not possible
		if (isIncludeFeatures()) {
			addPropertyTypes(classifier, javaClass);
			addOperationReferences(classifier, javaClass);
		}
		// correct referred types in 1st pass, as replacing referenced types in the 2nd pass is not possible
		if (isIncludeInstructionReferences()) {
			addOpCodeReferences(classifier, javaClass);
		}
		return true;
	}

	/**
	 * Adds interfaces implemented by javaClass to the UML model. Used in 1st pass.
	 * @param javaClass the Java class file to convert.
	 */
	public void addReferencedInterfaces(JavaClass javaClass) {
		String interfaces[] = javaClass.getInterfaceNames();
		for (int i = 0; i < interfaces.length; i++) {
			Classifier iface = findContainedClassifier.findClassifier(
					getModel(), interfaces[i], UMLPackage.eINSTANCE.getInterface());
			if (!(iface instanceof Interface)) {
				replaceByClassifier.setClassifier(iface);
				replaceByClassifier.setMetaClass(UMLPackage.eINSTANCE.getInterface());
				iface = (Classifier) replaceByClassifier.doSwitch(iface.getOwner());
			}
			iface.setIsLeaf(false);
		}
	}

	/**
	 * Adds superclass of javaClass to the UML model. Used in 1st pass.
	 * @param javaClass The Java class file to convert.
	 */
	public void addReferencedGenerals(JavaClass javaClass) {
		if (!"java.lang.Object".equals(javaClass.getClassName())) { //$NON-NLS-1$
			Classifier superClass = findContainedClassifier.findClassifier(
					getModel(), javaClass.getSuperclassName(), UMLPackage.eINSTANCE.getClass_());
			if (!(superClass instanceof Class)) {
				replaceByClassifier.setClassifier(superClass);
				replaceByClassifier.setMetaClass(UMLPackage.eINSTANCE.getClass_());
				superClass = (Classifier) replaceByClassifier.doSwitch(superClass.getOwner());
			}
		}
	}

	/**
	 * Adds interface realizations to classifier for each interface implemented
	 * by javaClass. Used in 1st pass.
	 * @param classifier The classifier representation of javaClass.
	 * @param javaClass The Java class file to convert.
	 */
	public void addInterfaceRealizations(Classifier classifier, JavaClass javaClass) {
		Assert.assertNotNull(classifier);
		String interfaces[] = javaClass.getInterfaceNames();
		for (int i = 0; i < interfaces.length; i++) {
			Classifier iface = findContainedClassifier.findClassifier(
					getModel(), interfaces[i], null);
			Assert.assertTrue(iface instanceof Interface);
			addClassifierInterface.setIface((Interface) iface);
			addClassifierInterface.doSwitch(classifier);
		}
	}

	/**
	 * Adds generalizations to classifier for each superclass
	 * of javaClass. Used in 1st pass.
	 * @param classifier The classifier representation of javaClass.
	 * @param javaClass The Java class file to convert.
	 */
	public void addGeneralizations(Classifier classifier, JavaClass javaClass) {
		Assert.assertNotNull(classifier);
		if (classifier instanceof Interface) {
			return;
		}
		if (!classifier.getQualifiedName().endsWith("java::lang::Object")) { //$NON-NLS-1$
			Classifier superClass = findContainedClassifier.findClassifier(
					getModel(), javaClass.getSuperclassName(), UMLPackage.eINSTANCE.getClass_());
			if (superClass != null) {
				Assert.assertTrue(superClass instanceof Class);
				classifier.createGeneralization(superClass);
			}
		}
	}

	/**
	 * Adds property types to the model for each javaClass field.
	 * @param classifier The classifier representation of javaClass.
	 * @param javaClass The Java class file to convert.
	 * @throws JarToUMLException 
	 */
	public void addPropertyTypes(Classifier classifier, JavaClass javaClass) throws JarToUMLException {
		Assert.assertNotNull(classifier);
		Field[] fields = javaClass.getFields();
		for (int i = 0; i < fields.length; i++) {
			if (!filter(fields[i])) {
				continue;
			}
			JarToUML.logger.finest(fields[i].getSignature());
			addClassifierProperty.setPropertyName(fields[i].getName());
			addClassifierProperty.setBCELPropertyType(fields[i].getType());
			if (addClassifierProperty.getPropertyType() == null) {
				throw new JarToUMLException(String.format(
						JarToUMLResources.getString("typeNotFoundFor"), 
						javaClass.getClassName(),
						fields[i].getName(),
						fields[i].getType().getSignature())); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Adds referenced types to the model for each javaClass method.
	 * @param classifier The classifier representation of javaClass.
	 * @param javaClass The Java class file to convert.
	 * @throws JarToUMLException 
	 */
	public void addOperationReferences(Classifier classifier, JavaClass javaClass) throws JarToUMLException {
		Assert.assertNotNull(classifier);
		Method[] methods = javaClass.getMethods();
		for (int i = 0; i < methods.length; i++) {
			if (getFilter() != null) {
				if (!getFilter().filter(methods[i])) {
					continue;
				}
			}
			JarToUML.logger.finest(methods[i].getSignature());
			org.apache.bcel.generic.Type[] types = methods[i].getArgumentTypes();
			addClassifierOperation.setOperationName(methods[i].getName());
			addClassifierOperation.setBCELArgumentTypes(types);
			addClassifierOperation.setBCELReturnType(methods[i].getReturnType());
		}
	}

	/**
	 * Adds the classifiers referenced by the bytecode instructions of javaClass
	 * to the UML model. Used in 1st pass.
	 * @param classifier The classifier representation of javaClass.
	 * @param javaClass The Java class file to convert.
	 */
	public void addOpCodeReferences(Classifier classifier, JavaClass javaClass) {
		Assert.assertNotNull(classifier);
		Method[] methods = javaClass.getMethods();
		for (int i = 0; i < methods.length; i++) {
			if (getFilter() != null) {
				if (!getFilter().filter(methods[i])) {
					continue;
				}
			}
			JarToUML.logger.finest(methods[i].getSignature());
			addOpCodeRefs(classifier, methods[i]);
		}
	}

	/**
	 * Adds the classifiers referenced by the bytecode instructions of method
	 * to the UML model. Used in 1st pass.
	 * @param instrContext The classifier on which the method is defined.
	 * @param method The method for which to convert the references.
	 */
	public void addOpCodeRefs(Classifier instrContext, Method method) {
		if (method.getCode() == null) {
			return;
		}
		addInstructionReferences.setCp(method.getConstantPool());
		InstructionList instrList = new InstructionList(method.getCode().getCode());
		Instruction[] instr = instrList.getInstructions();
		for (int i = 0; i < instr.length; i++) {
			instr[i].accept(addInstructionReferences);
		}
	}

}