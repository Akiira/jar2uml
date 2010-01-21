package be.ac.vub.jar2uml;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.Assert;

import org.apache.bcel.classfile.AccessFlags;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.StackMap;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionList;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Comment;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Interface;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Operation;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.PackageableElement;
import org.eclipse.uml2.uml.PrimitiveType;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.UMLFactory;
import org.eclipse.uml2.uml.UMLPackage;
import org.eclipse.uml2.uml.VisibilityKind;
import org.eclipse.uml2.uml.resource.UMLResource;

import be.ac.vub.jar2uml.ui.JarToUMLPlugin;

/**
 * Main class for the jar to UML converter. Start conversion by invoking {@link #run()}. 
 * @author Dennis Wagelaar <dennis.wagelaar@vub.ac.be>
 */
public class JarToUML implements Runnable {
		
	public static final String LOGGER = "be.ac.vub.jar2uml";
	
	private static Logger logger = Logger.getLogger(LOGGER);
	private static FindContainedClassifierSwitch findContainedClassifier = new FindContainedClassifierSwitch();

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			JarToUML jarToUML = new JarToUML();
			jarToUML.setFilter(new JavaAPIFilter());
			jarToUML.addJar(new JarFile(args[0]));
			jarToUML.setOutputFile(args[1]);
			jarToUML.setOutputModelName(args[2]);
			jarToUML.run();
			if (jarToUML.isRunComplete()) {
				jarToUML.getModel().eResource().save(Collections.EMPTY_MAP);
			}
		} catch (Exception e) {
			logger.severe("Usage: JarToUML <jarfile> <umlfile> <umlmodelname>");
			e.printStackTrace();
		}
	}

	private Model model = null;
	private List<JarFile> jars = new ArrayList<JarFile>();
	private List<IContainer> paths = new ArrayList<IContainer>();
	private RemoveClassifierSwitch removeClassifier = new RemoveClassifierSwitch();
	private ReplaceByClassifierSwitch replaceByClassifier = new ReplaceByClassifierSwitch();
	private TypeToClassifierSwitch typeToClassifier = new TypeToClassifierSwitch();
	private FixClassifierSwitch fixClassifier = new FixClassifierSwitch();
	private AddClassifierInterfaceSwitch addClassifierInterface = new AddClassifierInterfaceSwitch();
	private AddClassifierPropertySwitch addClassifierProperty = new AddClassifierPropertySwitch(typeToClassifier);
	private AddClassifierOperationSwitch addClassifierOperation = new AddClassifierOperationSwitch(typeToClassifier);
	private AddInstructionReferencesVisitor addInstructionReferences = 
		new AddInstructionReferencesVisitor(
				typeToClassifier);
	private AddInstructionDependenciesVisitor addInstructionDependencies = 
		new AddInstructionDependenciesVisitor(
				typeToClassifier,
				addClassifierProperty,
				addClassifierOperation);
	private Filter filter = null;
	private String outputFile = "api.uml";
	private String outputModelName = "api";
	private IProgressMonitor monitor = null;
	private boolean includeInstructionReferences = false;
	private boolean includeFeatures = true;
	private boolean dependenciesOnly = false;
	private int majorFormatVersion;
	private int minorFormatVersion;
	private boolean preverified;
	private boolean runComplete = false;

	public JarToUML() {
		logger.setLevel(Level.ALL);
	}

	/**
	 * Performs the actual jar to UML conversion.
	 */
	public void run() {
		setRunComplete(false);
		Assert.assertNotNull(getOutputFile());
		logger.info("Starting JarToUML for " + getOutputFile());
		ResourceSet resourceSet = createResourceSet();
		Resource res = resourceSet.createResource(URI.createURI(getOutputFile()));
		Assert.assertNotNull(res);
		try {
			setModel(UMLFactory.eINSTANCE.createModel());
			res.getContents().add(getModel());
			getModel().setName(getOutputModelName());
			Comment comment = getModel().createOwnedComment();
			comment.setBody(
					"Generated by Jar2UML " + 
					JarToUMLPlugin.getPlugin().getBundle().getVersion() +
					" from " + getInputList()); //NON-NLS-1
			typeToClassifier.setRoot(getModel());
			for (JarFile jar : getJars()) {
				addAllClassifiers(jar);
				if (monitor != null) {
					if (monitor.isCanceled()) {
						break;
					}
				}
			}
			for (IContainer path : getPaths()) {
				addAllClassifiers(path);
				if (monitor != null) {
					if (monitor.isCanceled()) {
						break;
					}
				}
			}
			for (JarFile jar : getJars()) {
				addAllProperties(jar);
				if (monitor != null) {
					if (monitor.isCanceled()) {
						break;
					}
				}
			}
			for (IContainer paths : getPaths()) {
				addAllProperties(paths);
				if (monitor != null) {
					if (monitor.isCanceled()) {
						break;
					}
				}
			}
			if (dependenciesOnly) {
				for (JarFile jar : getJars()) {
					removeAllClassifiers(jar);
					if (monitor != null) {
						if (monitor.isCanceled()) {
							break;
						}
					}
				}
				for (IContainer path : getPaths()) {
					removeAllClassifiers(path);
					if (monitor != null) {
						if (monitor.isCanceled()) {
							break;
						}
					}
				}
			}
			removeEmptyPackages(getModel());
			EAnnotation ann = getModel().createEAnnotation("Jar2UML");
			EMap<String,String> details = ann.getDetails();
			details.put("majorBytecodeFormatVersion", String.valueOf(getMajorFormatVersion()));
			details.put("minorBytecodeFormatVersion", String.valueOf(getMinorFormatVersion()));
			details.put("preverified", String.valueOf(isPreverified()));
			if ((monitor == null) || (!monitor.isCanceled())) {
				setRunComplete(true);
			} else {
				logger.info("JarToUML run cancelled");
			}
		} catch (Exception e) {
			StringWriter stackTrace = new StringWriter();
			e.printStackTrace(new PrintWriter(stackTrace));
			logger.severe(e.getLocalizedMessage());
			logger.severe(stackTrace.toString());
		}
		typeToClassifier.reset();
		logger.info("Finished JarToUML");
	}
	
	/**
	 * @return A new ResourceSet with support for UML models outside Eclipse
	 */
	public static ResourceSet createResourceSet() {
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getPackageRegistry().put(UMLPackage.eNS_URI,
				UMLPackage.eINSTANCE);
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().
			  	put(UMLResource.FILE_EXTENSION, UMLResource.Factory.INSTANCE);
		return resourceSet;
	}
	
	/**
	 * @return A comma-separated list of all Jar and Path inputs
	 */
	private String getInputList() {
		StringBuffer b = null;
		for (JarFile jar : getJars()) {
			if (b == null) {
				b = new StringBuffer();
			} else {
				b.append(", ");	
			}
			String jarName = jar.getName();
			b.append(jarName.substring(jarName.lastIndexOf('/') + 1));	
		}
		for (IContainer path : getPaths()) {
			if (b == null) {
				b = new StringBuffer();
			} else {
				b.append(", ");	
			}
			b.append(path.getFullPath());
		}
		return b.toString();
	}
	
	/**
	 * Adds all classifiers in jar to the UML model. Does not add classifier properties.
	 * @param jar The jar file to convert
	 * @throws IOException
	 */
	private void addAllClassifiers(JarFile jar) throws IOException {
		Assert.assertNotNull(jar);
		for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
			JarEntry entry = entries.nextElement();
			if (entry.getName().endsWith(".class")) {
				if (getFilter() != null) {
					if (!getFilter().filter(entry.getName())) {
						continue;
					}
				}
				InputStream input = jar.getInputStream(entry);
				ClassParser parser = new ClassParser(input, entry.getName());
				JavaClass javaClass = parser.parse();
				setMajorFormatVersion(javaClass.getMajor());
				setMinorFormatVersion(javaClass.getMinor());
				input.close();
				addClassifier(javaClass);
			}
			if (monitor != null) {
				if (monitor.isCanceled()) {
					break;
				}
			}
		}
		fixClassifier.reset();
	}
	
	/**
	 * Finds all .class files within parent
	 * @param parent
	 * @param cfs the list of found class files
	 * @throws CoreException
	 */
	public static void findClassFilesIn(IContainer parent, List<IFile> cfs)
			throws CoreException {
		for (IResource r : parent.members()) {
			switch (r.getType()) {
			case IResource.FILE:
				IFile file = (IFile) r;
				if (file.getFileExtension().equals("class")) {
					cfs.add(file);
				}
				break;
			case IResource.FOLDER:
			case IResource.PROJECT:
				findClassFilesIn((IContainer)r, cfs);
				break;
			}
		}
	}
	
	/**
	 * Adds all classifiers under container to the UML model. Does not add classifier properties.
	 * @param container The root path containing the files to convert
	 * @throws IOException
	 * @throws CoreException 
	 */
	private void addAllClassifiers(IContainer container) throws IOException, CoreException {
		Assert.assertNotNull(container);
		List<IFile> classFiles = new ArrayList<IFile>();
		findClassFilesIn(container, classFiles);
		for (IFile classFile : classFiles) {
			IPath filePath = classFile.getLocation();
			String filename = filePath.toString().substring(container.getLocation().toString().length());
			if (getFilter() != null) {
				if (!getFilter().filter(filename)) {
					continue;
				}
			}
			InputStream input = classFile.getContents();
			ClassParser parser = new ClassParser(input, filename);
			JavaClass javaClass = parser.parse();
			setMajorFormatVersion(javaClass.getMajor());
			setMinorFormatVersion(javaClass.getMinor());
			input.close();
			addClassifier(javaClass);
			if (monitor != null) {
				if (monitor.isCanceled()) {
					break;
				}
			}
		}
		fixClassifier.reset();
	}
	
	/**
	 * Adds the properties of all classifiers in jar to the classifiers in the UML model.
	 * @param jar
	 * @throws IOException
	 */
	private void addAllProperties(JarFile jar) throws IOException {
		Assert.assertNotNull(jar);
		for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
			JarEntry entry = entries.nextElement();
			if (entry.getName().endsWith(".class")) {
				if (getFilter() != null) {
					if (!getFilter().filter(entry.getName())) {
						continue;
					}
				}
				InputStream input = jar.getInputStream(entry);
				ClassParser parser = new ClassParser(input, entry.getName());
				JavaClass javaClass = parser.parse();
				input.close();
				addClassifierProperties(javaClass);
			}
			if (monitor != null) {
				if (monitor.isCanceled()) {
					break;
				}
			}
		}
	}
	
	/**
	 * Adds the properties of all classifiers under container to the classifiers in the UML model.
	 * @param container The root path containing the files to convert
	 * @throws IOException
	 * @throws CoreException 
	 * @throws JavaModelException 
	 */
	private void addAllProperties(IContainer container) throws IOException, CoreException {
		Assert.assertNotNull(container);
		List<IFile> classFiles = new ArrayList<IFile>();
		findClassFilesIn(container, classFiles);
		for (IFile classFile : classFiles) {
			IPath filePath = classFile.getLocation();
			String filename = filePath.toString().substring(container.getLocation().toString().length());
			if (getFilter() != null) {
				if (!getFilter().filter(filename)) {
					continue;
				}
			}
			InputStream input = classFile.getContents();
			ClassParser parser = new ClassParser(input, filename);
			JavaClass javaClass = parser.parse();
			input.close();
			addClassifierProperties(javaClass);
			if (monitor != null) {
				if (monitor.isCanceled()) {
					break;
				}
			}
		}
	}

	/**
	 * Removes all classifiers in jar from the UML model.
	 * @param jar The jar file to convert
	 * @throws IOException
	 */
	private void removeAllClassifiers(JarFile jar) throws IOException {
		Assert.assertNotNull(jar);
		for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
			JarEntry entry = entries.nextElement();
			if (entry.getName().endsWith(".class")) {
				if (getFilter() != null) {
					if (!getFilter().filter(entry.getName())) {
						continue;
					}
				}
				InputStream input = jar.getInputStream(entry);
				ClassParser parser = new ClassParser(input, entry.getName());
				JavaClass javaClass = parser.parse();
				input.close();
				removeClassifier(javaClass);
			}
			if (monitor != null) {
				if (monitor.isCanceled()) {
					break;
				}
			}
		}
		removeClassifier.reset();
	}
	
	/**
	 * Removes all classifiers under container from the UML model.
	 * @param container The root path containing the files to convert
	 * @throws IOException
	 * @throws CoreException 
	 * @throws JavaModelException 
	 */
	private void removeAllClassifiers(IContainer container) throws IOException, CoreException {
		Assert.assertNotNull(container);
		List<IFile> classFiles = new ArrayList<IFile>();
		findClassFilesIn(container, classFiles);
		for (IFile classFile : classFiles) {
			IPath filePath = classFile.getLocation();
			String filename = filePath.toString().substring(container.getLocation().toString().length());
			if (getFilter() != null) {
				if (!getFilter().filter(filename)) {
					continue;
				}
			}
			InputStream input = classFile.getContents();
			ClassParser parser = new ClassParser(input, filename);
			JavaClass javaClass = parser.parse();
			input.close();
			removeClassifier(javaClass);
			if (monitor != null) {
				if (monitor.isCanceled()) {
					break;
				}
			}
		}
		removeClassifier.reset();
	}

	/**
	 * Finds a package in the UML model, starting from root.
	 * @param root The root node in the UML model to search under.
	 * @param packageName The qualified Java package name relative to root.
	 * @param create If true, a new package is created if the package is not found.
	 * @return The requested package or null if create is false and no package is found.
	 */
	public static Package findPackage(Package root, String packageName, boolean create) {
		Assert.assertNotNull(packageName);
		Package parent = root;
		final String tail = tail(packageName, '.');
		if (tail.length() < packageName.length()) {
			String parentName = packageName.substring(0, packageName.length() - tail.length() - 1);
			parent = findPackage(root, parentName, create);
			packageName = tail;
		}
		if (parent == null) {
			return null;
		}
		for (Iterator<Package> it = parent.getNestedPackages().iterator(); it.hasNext();) {
			Package pack = it.next();
			if (packageName.equals(pack.getName())) {
				return pack;
			}
		}
		if (create) {
			return parent.createNestedPackage(packageName);
		} else {
			return null;
		}
	}
	
	/**
	 * Adds a classifier to the UML model that represents javaClass. Does not add classifier properties.
	 * @param javaClass The BCEL class representation to convert.
	 */
	private void addClassifier(JavaClass javaClass) {
		final String className = javaClass.getClassName();
		if (getFilter() != null) {
			if (!getFilter().filter(javaClass)) {
				logger.fine("Skipped non-API class: " + className);
				return;
			}
		}
		logger.fine(className);
		final Classifier classifier = 
			findClassifier(getModel(), className, UMLPackage.eINSTANCE.getClass_());
		Assert.assertNotNull(classifier);
		fixClassifier.setJavaClass(javaClass);
		fixClassifier.doSwitch(classifier);
		addReferencedInterfaces(javaClass);
		addReferencedGenerals(javaClass);
		// add realizations/generalizations in 1st pass, since inheritance hierarchy is needed in 2nd pass
		addInterfaceRealizations(classifier, javaClass);
		addGeneralizations(classifier, javaClass);
		if (isIncludeInstructionReferences()) {
			addOpCodeReferences(classifier, javaClass);
		}
	}
	
	/**
	 * Adds the properties of the javaClass to the corresponding classifier in the UML model.
	 * @param javaClass The BCEL class representation to convert.
	 */
	private void addClassifierProperties(JavaClass javaClass) {
		final String className = javaClass.getClassName();
		if (getFilter() != null) {
			if (!getFilter().filter(javaClass)) {
				logger.fine("Skipped non-API class: " + className);
				return;
			}
		}
		logger.fine(className);
		final Classifier classifier = 
			findClassifier(getModel(), className, null);
		if (isIncludeFeatures()) {
			addProperties(classifier, javaClass);
			addOperations(classifier, javaClass);
		}
	}

	/**
	 * Remove the classifier corresponding to javaClass from the UML model.
	 * @param javaClass
	 */
	private void removeClassifier(JavaClass javaClass) {
		final String className = javaClass.getClassName();
		if (getFilter() != null) {
			if (!getFilter().filter(javaClass)) {
				logger.fine("Skipped non-API class: " + className);
				return;
			}
		}
		logger.fine(className);
		final Classifier classifier = 
			findClassifier(getModel(), className, null);
		if (classifier != null) {
			removeClassifier(classifier);
		}
	}
	
	/**
	 * Remove classifier from the UML model. Also removes derived and contained
	 * classifiers.
	 * @param classifier
	 */
	private void removeClassifier(Classifier classifier) {
		Assert.assertNotNull(classifier);
		final List<Classifier> derived = findDerivedClassifiers(classifier);
		final Element owner = classifier.getOwner();
		Assert.assertNotNull(owner);
		for (Iterator<Classifier> it = derived.iterator(); it.hasNext();) {
			removeClassifier.setClassifier(it.next());
			removeClassifier.doSwitch(owner);
		}
		removeClassifier.setClassifier(classifier);
		removeClassifier.doSwitch(owner);
	}
	
	/**
	 * Recursively removes empty packages from fromPackage.
	 * @param fromPackage
	 */
	private void removeEmptyPackages(Package fromPackage) {
		for (Iterator<PackageableElement> it = fromPackage.getPackagedElements().iterator(); it.hasNext();) {
			if (monitor != null) {
				if (monitor.isCanceled()) {
					break;
				}
			}
			PackageableElement o = it.next();
			if (o instanceof Package) {
				Package pack = (Package) o;
				removeEmptyPackages(pack);
				if (pack.getPackagedElements().isEmpty()) {
					it.remove();
					logger.fine("Removed " + pack.getQualifiedName() + " : " + 
							pack.eClass().getName());
				}
			}
		}
	}

	/**
	 * Adds interfaces implemented by javaClass to the UML model. Used in 1st pass.
	 * @param javaClass The Java class file to convert.
	 */
	private void addReferencedInterfaces(JavaClass javaClass) {
		String interfaces[] = javaClass.getInterfaceNames();
		for (int i = 0; i < interfaces.length; i++) {
			Classifier iface = findClassifier(getModel(), interfaces[i], UMLPackage.eINSTANCE.getInterface());
			if (!(iface instanceof Interface)) {
				replaceByClassifier.setClassifier(iface);
				replaceByClassifier.setMetaClass(UMLPackage.eINSTANCE.getInterface());
				iface = (Classifier) replaceByClassifier.doSwitch(iface.getOwner());
			}
            iface.setIsLeaf(false);
		}
		replaceByClassifier.reset();
	}
	
	/**
	 * Adds interface realizations to classifier for each interface implemented
	 * by javaClass. Used in 2nd pass.
	 * @param classifier The classifier representation of javaClass.
	 * @param javaClass The Java class file to convert.
	 */
	private void addInterfaceRealizations(Classifier classifier, JavaClass javaClass) {
		Assert.assertNotNull(classifier);
		String interfaces[] = javaClass.getInterfaceNames();
		for (int i = 0; i < interfaces.length; i++) {
			Classifier iface = findClassifier(getModel(), interfaces[i], UMLPackage.eINSTANCE.getInterface());
			Assert.assertTrue(iface instanceof Interface);
			addClassifierInterface.setIface((Interface) iface);
			addClassifierInterface.doSwitch(classifier);
		}
		addClassifierInterface.reset();
	}
	
	/**
	 * Adds superclasses of javaClass to the UML model. Used in 1st pass.
	 * @param javaClass The Java class file to convert.
	 */
	private void addReferencedGenerals(JavaClass javaClass) {
		if (!"java.lang.Object".equals(javaClass.getSuperclassName())) {
			Classifier superClass = findClassifier(getModel(), javaClass.getSuperclassName(), UMLPackage.eINSTANCE.getClass_());
			if (superClass != null) {
				if (!(superClass instanceof Class)) {
					replaceByClassifier.setClassifier(superClass);
					replaceByClassifier.setMetaClass(UMLPackage.eINSTANCE.getClass_());
					superClass = (Classifier) replaceByClassifier.doSwitch(superClass.getOwner());
				}
			}
		}
		replaceByClassifier.reset();
	}

	/**
	 * Adds generalizations to classifier for each superclass
	 * of javaClass. Used in 2nd pass.
	 * @param classifier The classifier representation of javaClass.
	 * @param javaClass The Java class file to convert.
	 */
	private void addGeneralizations(Classifier classifier, JavaClass javaClass) {
		Assert.assertNotNull(classifier);
		if (classifier instanceof Interface) {
			return;
		}
		if (!classifier.getQualifiedName().endsWith("java::lang::Object")) {
		//if (!"java.lang.Object".equals(javaClass.getSuperclassName())) {
			Classifier superClass = findClassifier(getModel(), javaClass.getSuperclassName(), UMLPackage.eINSTANCE.getClass_());
			if (superClass != null) {
				Assert.assertTrue(superClass instanceof Class);
				classifier.createGeneralization(superClass);
			}
		}
	}
	
	/**
	 * Adds a property to classifier for each javaClass field.
	 * @param classifier The classifier representation of javaClass.
	 * @param javaClass The Java class file to convert.
	 */
	private void addProperties(Classifier classifier, JavaClass javaClass) {
		Assert.assertNotNull(classifier);
		Field[] fields = javaClass.getFields();
		for (int i = 0; i < fields.length; i++) {
			if (getFilter() != null) {
				if (!getFilter().filter(fields[i])) {
					continue;
				}
			}
			logger.fine(fields[i].getSignature());
			addClassifierProperty.setPropertyName(fields[i].getName());
			addClassifierProperty.setBCELPropertyType(fields[i].getType());
			if (addClassifierProperty.getPropertyType() == null) {
				logger.warning("Type not found for " + 
						javaClass.getClassName() + "#" + 
						fields[i].getName() + " : " + 
						fields[i].getType().getSignature());
			}
			Property prop = (Property) addClassifierProperty.doSwitch(classifier);
			prop.setVisibility(toUMLVisibility(fields[i]));
			prop.setIsStatic(fields[i].isStatic());
			prop.setIsReadOnly(fields[i].isFinal());
			prop.setIsLeaf(fields[i].isFinal());
		}
		addClassifierProperty.reset();
	}
	
	/**
	 * Adds an operation to classifier for each javaClass method.
	 * @param classifier The classifier representation of javaClass.
	 * @param javaClass The Java class file to convert.
	 */
	private void addOperations(Classifier classifier, JavaClass javaClass) {
		Assert.assertNotNull(classifier);
		Method[] methods = javaClass.getMethods();
		for (int i = 0; i < methods.length; i++) {
			if (getFilter() != null) {
				if (!getFilter().filter(methods[i])) {
					continue;
				}
			}
			logger.fine(methods[i].getSignature());
			org.apache.bcel.generic.Type[] types = methods[i].getArgumentTypes();
			addClassifierOperation.setOperationName(methods[i].getName());
			addClassifierOperation.setBCELArgumentTypes(types);
			addClassifierOperation.setBCELReturnType(methods[i].getReturnType());
			Operation op = (Operation) addClassifierOperation.doSwitch(classifier);
			op.setVisibility(toUMLVisibility(methods[i]));
			op.setIsAbstract(methods[i].isAbstract());
			op.setIsStatic(methods[i].isStatic());
			op.setIsLeaf(methods[i].isFinal());
			if (isIncludeInstructionReferences()) {
				addOpCode(classifier, methods[i]);
			}
			if (isPreverified(methods[i].getCode())) {
				setPreverified(true);
			}
		}
		addClassifierOperation.reset();
	}
	
	/**
	 * Adds fields/methods referenced by the bytecode instructions of method
	 * to the UML model. Used in 2nd pass.
	 * @param instrContext The classifier on which the method is defined.
	 * @param method The method for which to convert the references.
	 */
	private void addOpCode(Classifier instrContext, Method method) {
		if (method.getCode() == null) {
			return;
		}
		addInstructionDependencies.setInstrContext(instrContext);
		addInstructionDependencies.setCp(method.getConstantPool());
		InstructionList instrList = new InstructionList(method.getCode().getCode());
		Instruction[] instr = instrList.getInstructions();
		for (int i = 0; i < instr.length; i++) {
			instr[i].accept(addInstructionDependencies);
		}
		addInstructionDependencies.reset();
	}
	
	/**
	 * Adds the classifiers referenced by the bytecode instructions of javaClass
	 * to the UML model. Used in 1st pass.
	 * @param classifier The classifier representation of javaClass.
	 * @param javaClass The Java class file to convert.
	 */
	private void addOpCodeReferences(Classifier classifier, JavaClass javaClass) {
		Assert.assertNotNull(classifier);
		Method[] methods = javaClass.getMethods();
		for (int i = 0; i < methods.length; i++) {
			if (getFilter() != null) {
				if (!getFilter().filter(methods[i])) {
					continue;
				}
			}
			logger.fine(methods[i].getSignature());
			addOpCodeRefs(classifier, methods[i]);
		}
	}
	
	/**
	 * Adds the classifiers referenced by the bytecode instructions of method
	 * to the UML model. Used in 1st pass.
	 * @param instrContext The classifier on which the method is defined.
	 * @param method The method for which to convert the references.
	 */
	private void addOpCodeRefs(Classifier instrContext, Method method) {
		if (method.getCode() == null) {
			return;
		}
		addInstructionReferences.setCp(method.getConstantPool());
		InstructionList instrList = new InstructionList(method.getCode().getCode());
		Instruction[] instr = instrList.getInstructions();
		for (int i = 0; i < instr.length; i++) {
			instr[i].accept(addInstructionReferences);
		}
		addInstructionReferences.reset();
	}
	
	/**
	 * @param javaName The string to split.
	 * @param delim The delimiter used to split javaName in parts.
	 * @return The last section of the javaName string separated by delim.
	 */
	public static String tail(String javaName, char delim) {
		int dotIndex = javaName.lastIndexOf(delim);
		if (dotIndex > -1) {
			return javaName.substring(dotIndex+1, javaName.length());
		} else {
			return javaName;
		}
	}
	
	/**
	 * Finds the primitive type with name typeName in the UML model.
	 * @param root The model root to start searching at.
	 * @param typeName The fully qualified type name (e.g. "java.lang.int").
	 * @param create If true, create a new primitive type if not found.
	 * @return the {@link PrimitiveType} with name typeName. If create is true and
	 * the primitive type is not found, a new instance of {@link PrimitiveType} is
	 * created and returned.
	 */
	public static PrimitiveType findPrimitiveType(Package root, String typeName, boolean create) {
		Assert.assertNotNull(typeName);
		String localTypeName = typeName;
		Package parent = root;
		String tail = tail(typeName, '.');
		if (tail.length() < typeName.length()) {
			String parentName = typeName.substring(0, typeName.length() - tail.length() - 1);
			parent = findPackage(root, parentName, create);
			localTypeName = tail;
		}
		if (parent == null) {
			return null;
		}
		for (Iterator<PackageableElement> it = parent.getPackagedElements().iterator(); it.hasNext();) {
			PackageableElement element = it.next();
			if (element instanceof PrimitiveType) {
				PrimitiveType type = (PrimitiveType) element;
				if (localTypeName.equals(type.getName())) {
					return type;
				}
			}
		}
		if (create) {
			return parent.createOwnedPrimitiveType(localTypeName);
		}
		return null;
	}
	
	/**
	 * @param root The model root to start searching at.
	 * @param className The fully qualified classifier name (e.g. "java.lang.Class$Inner")
	 * @param createAs If not null and classifier is not found, an instance of this meta-class is created.
	 * @return A {@link Classifier} with qualified name className. If createAs is not null
	 * and the classifier was not found, a new instance of the createAs meta-class is
	 * created and returned.
	 */
	public static Classifier findClassifier(Package root, String className, EClass createAs) {
		Assert.assertNotNull(className);
		String localClassName = className;
		Package parent = root;
		String tail = tail(className, '.');
		if (tail.length() < className.length()) {
			String parentName = className.substring(0, className.length() - tail.length() - 1);
			parent = findPackage(root, parentName, createAs != null);
			localClassName = tail;
		}
		if (parent == null) {
			return null;
		}
		Classifier parentClass = null;
		tail = tail(className, '$');
		if (tail.length() < className.length()) {
			String parentName = className.substring(0, className.length() - tail.length() - 1);
			parentClass = findClassifier(root, parentName, createAs);
			localClassName = tail;
		}
		if (parentClass != null) {
			return findLocalClassifier(parentClass, localClassName, createAs);
		} else {
			return findLocalClassifier(parent, localClassName, createAs);
		}
	}
	
	/**
	 * @param parent The parent element to search at, e.g. "java.lang.Class".
	 * @param localClassName The local classifier name (e.g. "Inner")
	 * @param createAs If not null and classifier is not found, an instance of this meta-class is created.
	 * @return A {@link Classifier} with name localClassName. If createAs is not null
	 * and the classifier was not found, a new instance of the createAs meta-class is
	 * created and returned.
	 */
	public static Classifier findLocalClassifier(Element parent, String localClassName, EClass createAs) {
		Assert.assertNotNull(parent);
		Assert.assertNotNull(localClassName);
		findContainedClassifier.setClassifierName(localClassName);
		if (createAs != null) {
			findContainedClassifier.setMetaClass(createAs);
			findContainedClassifier.setCreate(true);
		} else {
			findContainedClassifier.setCreate(false);
		}
		return findContainedClassifier.doSwitch(parent);
	}
	
	/**
	 * @param classifier
	 * @return All classifiers that are derivatives (i.e. array types) of classifier.
	 */
	public static List<Classifier> findDerivedClassifiers(Classifier classifier) {
		Assert.assertNotNull(classifier);
		final String name = classifier.getName();
		Assert.assertNotNull(name);
		final List<Classifier> derived = new ArrayList<Classifier>();
		final Element owner = classifier.getOwner();
		Assert.assertNotNull(owner);
		for (Iterator<Element> owned = owner.getOwnedElements().iterator(); owned.hasNext();) {
			Element e = owned.next();
			if (e instanceof Classifier) {
				Classifier c = (Classifier) e;
				String cname = c.getName();
				Assert.assertNotNull(cname);
				if (cname.startsWith(name)) {
					cname = cname.substring(name.length());
					cname = cname.replace('[', ' ');
					cname = cname.replace(']', ' ');
					cname = cname.trim();
					if (cname.length() == 0) {
						derived.add(c);
					}
				}
			}
		}
		return derived;
	}
	
	/**
	 * @param javaClass
	 * @return True if the name of javaClass does not parse as an Integer.
	 */
	public static boolean isNamedClass(JavaClass javaClass) {
		String leafName = tail(javaClass.getClassName(), '$');
		try {
			Integer.parseInt(leafName);
			return false;
		} catch (NumberFormatException e) {
			//everything allright, not an anonymous class
		}
		return true;
	}

	/**
	 * @param code
	 * @return True if the code has been preverified for CLDC execution, i.e. it has a StackMap attribute
	 */
	public static boolean isPreverified(Code code) {
		if (code == null) {
			return false;
		}
		for (Attribute att : code.getAttributes()) {
			if (att instanceof StackMap) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * @param flags
	 * @return The UML representation of flags.
	 */
	public static VisibilityKind toUMLVisibility(AccessFlags flags) {
		if (flags.isPublic()) {
			return VisibilityKind.PUBLIC_LITERAL;
		} else if (flags.isProtected()) {
			return VisibilityKind.PROTECTED_LITERAL;
		} else {
			return VisibilityKind.PRIVATE_LITERAL;
		}
	}

	/**
	 * Adds jar to the collection of jars to process.
	 * @param jar
	 */
	public void addJar(JarFile jar) {
		jars.add(jar);
	}
	
	/**
	 * Removes jar from the collection of jars to process.
	 * @param jar
	 */
	public void removeJar(JarFile jar) {
		jars.remove(jar);
	}
	
	/**
	 * Clears the collection of jars to process.
	 */
	public void clearJars() {
		jars.clear();
	}
	
	/**
	 * @return The collection of jars to process.
	 */
	public List<JarFile> getJars() {
		return jars;
	}

	/**
	 * @return The generated UML model.
	 */
	public Model getModel() {
		return model;
	}

	/**
	 * Sets the UML model to store generated elements in.
	 * @param model
	 */
	protected void setModel(Model model) {
		this.model = model;
	}

	/**
	 * @return The Java element filter to apply.
	 */
	public Filter getFilter() {
		return filter;
	}

	/**
	 * Sets the Java element filter to apply.
	 * @param filter
	 */
	public void setFilter(Filter filter) {
		this.filter = filter;
		//addConstantPoolEntries.setFilter(filter);
	}

	/**
	 * @return The output file location (understandable to EMF).
	 */
	public String getOutputFile() {
		return outputFile;
	}

	/**
	 * Sets the output file location (understandable to EMF).
	 * @param outputFile
	 */
	public void setOutputFile(String outputFile) {
		this.outputFile = outputFile;
	}

	/**
	 * @return The name of the UML Model root element.
	 */
	public String getOutputModelName() {
		return outputModelName;
	}

	/**
	 * Sets the name of the UML Model root element.
	 * @param outputModelName
	 */
	public void setOutputModelName(String outputModelName) {
		this.outputModelName = outputModelName;
	}

	/**
	 * @return The progress monitor object used to check for cancellations.
	 */
	public IProgressMonitor getMonitor() {
		return monitor;
	}

	/**
	 * Sets the progress monitor object used to check for cancellations.
	 * @param monitor
	 */
	public void setMonitor(IProgressMonitor monitor) {
		this.monitor = monitor;
	}

	/**
	 * @return Whether or not to include Java elements that are only
	 * referred to by bytecode instructions. Defaults to false.
	 */
	public boolean isIncludeInstructionReferences() {
		return includeInstructionReferences;
	}

	/**
	 * Sets whether or not to include Java elements that are only
	 * referred to by bytecode instructions. Defaults to false.
	 * @param includeInstructionReferences
	 */
	public void setIncludeInstructionReferences(boolean includeInstructionReferences) {
		this.includeInstructionReferences = includeInstructionReferences;
	}

	/**
	 * Whether or not to include classifier operations and attributes. Defaults to true.
	 * @return the includeFeatures
	 */
	public boolean isIncludeFeatures() {
		return includeFeatures;
	}

	/**
	 * Whether or not to include classifier operations and attributes. Defaults to true.
	 * @param includeFeatures the includeFeatures to set
	 */
	public void setIncludeFeatures(boolean includeFeatures) {
		this.includeFeatures = includeFeatures;
	}

	/**
	 * If true, includes only the dependencies instead of the contained elements.
	 * @return the dependenciesOnly
	 */
	public boolean isDependenciesOnly() {
		return dependenciesOnly;
	}

	/**
	 * If true, includes only the dependencies instead of the contained elements.
	 * @param dependenciesOnly the dependenciesOnly to set
	 */
	public void setDependenciesOnly(boolean dependenciesOnly) {
		this.dependenciesOnly = dependenciesOnly;
	}

	/**
	 * The class file format major version. 
	 * @return the majorFormatVersion
	 * {@link http://en.wikipedia.org/wiki/Class_%28file_format%29}
	 */
	public int getMajorFormatVersion() {
		return majorFormatVersion;
	}

	/**
	 * The class file format minor version. 
	 * @param majorFormatVersion the majorFormatVersion to set
	 * {@link http://en.wikipedia.org/wiki/Class_%28file_format%29}
	 */
	protected void setMajorFormatVersion(int majorFormatVersion) {
		this.majorFormatVersion = Math.max(this.majorFormatVersion, majorFormatVersion);
	}

	/**
	 * @return the minorFormatVersion
	 */
	public int getMinorFormatVersion() {
		return minorFormatVersion;
	}

	/**
	 * @param minorFormatVersion the minorFormatVersion to set
	 */
	protected void setMinorFormatVersion(int minorFormatVersion) {
		this.minorFormatVersion = Math.max(this.minorFormatVersion, minorFormatVersion);
	}

	/**
	 * Whether or not the bytecode has been preverified for execution on J2ME CLDC.
	 * @return the preverified
	 */
	public boolean isPreverified() {
		return preverified;
	}

	/**
	 * @param preverified the preverified to set
	 */
	protected void setPreverified(boolean preverified) {
		this.preverified = preverified;
	}

	/**
	 * @return the paths
	 */
	public List<IContainer> getPaths() {
		return paths;
	}

	/**
	 * Empties the list of paths
	 */
	public void clearPaths() {
		this.paths.clear();
	}

	/**
	 * @param path the path to add
	 */
	public void addPath(IContainer path) {
		this.paths.add(path);
	}

	/**
	 * @param path the path to remove
	 */
	public void removePath(IContainer path) {
		this.paths.remove(path);
	}

	/**
	 * Adds all relevant class file paths for javaProject
	 * @param javaProject
	 * @param includeWorkspaceReferences Include referenced projects and jar files in workspace
	 * @throws JavaModelException 
	 * @throws IOException 
	 */
	public void addPaths(IJavaProject javaProject, boolean includeWorkspaceReferences) throws JavaModelException, IOException {
		for (IClasspathEntry cpe : javaProject.getResolvedClasspath(true)) {
			IPath cpePath;
		   	switch (cpe.getEntryKind()) {
		   	case IClasspathEntry.CPE_SOURCE:
		   		cpePath = cpe.getOutputLocation();
		   		if (cpePath == null) {
		   			cpePath = javaProject.getOutputLocation();
		   		}
		   		IContainer container = (IContainer) 
		   			ResourcesPlugin.getWorkspace().getRoot().findMember(cpePath);
		   		addPath(container);
		   		break;
		   	case IClasspathEntry.CPE_LIBRARY:
		   		cpePath = cpe.getPath();
		   		IResource resource = 
		   			ResourcesPlugin.getWorkspace().getRoot().findMember(cpePath);
				if ((resource != null) && 
					(includeWorkspaceReferences 
						|| javaProject.getProject().equals(resource.getProject()))) {
					addJar(new JarFile(resource.getLocation().toFile()));
				}
		   		break;
		   	}
		}
		if (includeWorkspaceReferences) {
			Set<IJavaProject> refs = new HashSet<IJavaProject>();
			findJavaProjectReferences(javaProject, refs);
			for (IJavaProject ref : refs) {
				addPaths(ref, includeWorkspaceReferences);
			}
		}
	}

	/**
	 * @param path
	 * @return The Java project for the given path, or null
	 */
	public static IJavaProject getJavaProject(IPath path) {
		IJavaModel model = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
		IResource resource = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
		IProject project = resource.getProject();
		return model.getJavaProject(project.getName());
	}

	/**
	 * Retrieves the project references for javaProject and stores them in refs
	 * @param javaProject
	 * @param refs the project references for javaProject
	 */
	public static void findJavaProjectReferences(IJavaProject javaProject, Set<IJavaProject> refs) {
		try {
			for (IClasspathEntry cpe : javaProject.getResolvedClasspath(true)) {
				IPath cpePath;
			   	switch (cpe.getEntryKind()) {
			   	case IClasspathEntry.CPE_PROJECT:
			   		cpePath = cpe.getPath();
			   		IJavaProject ref = getJavaProject(cpePath);
			   		refs.add(ref);
			   		break;
			   	}
			}
		} catch (JavaModelException e) {
			JarToUMLPlugin.getPlugin().report(e);
		}
	}

	/**
	 * @return the runComplete
	 */
	public boolean isRunComplete() {
		return runComplete;
	}

	/**
	 * @param runComplete the runComplete to set
	 */
	public void setRunComplete(boolean runComplete) {
		this.runComplete = runComplete;
	}

}
