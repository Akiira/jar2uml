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
package org.eclipselabs.jar2uml.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

import org.apache.bcel.classfile.JavaClass;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipselabs.jar2uml.JarToUMLResources;
import org.eclipselabs.jar2uml.ParseClasses;

/**
 * Test class for {@link ParseClasses}.
 * @author Dennis Wagelaar <dennis.wagelaar@vub.ac.be>
 */
public class ParseClassesTest extends J2UTestCase {

	public static final String javatestProject = "ParseClassesTest";

	/**
	 * Test method for {@link org.eclipselabs.jar2uml.ParseClasses#ParseClasses(org.eclipselabs.jar2uml.Filter, org.eclipse.core.runtime.IProgressMonitor)}.
	 */
	public void testParseClasses() {
		final ParseClasses pc = new ParseClasses(null, null, 0);
		assertNotNull(pc);
	}

	/**
	 * Test method for {@link org.eclipselabs.jar2uml.ParseClasses#findClassFilesIn(org.eclipse.core.resources.IContainer, java.util.List)}.
	 * @throws IOException 
	 * @throws CoreException 
	 */
	public void testFindClassFilesIn() throws CoreException, IOException {
		//
		// Retrieve Java test project
		//
		final IProject project = getProject(javatestProject);
		//
		// Copy "JarToUMLTest.class" into Java project
		//
		final IFile classFile = copyClassToJavaProject(ParseClassesTest.class, project);
		//
		// Find the copied class file
		//
		final List<IFile> cfs = new ArrayList<IFile>();
		ParseClasses.findClassFilesIn(project, cfs);
		JarToUMLResources.logger.info("Class files in project: " + cfs);
		assertFalse(cfs.isEmpty());
		assertTrue(cfs.contains(classFile));
	}

	/**
	 * Test method for {@link org.eclipselabs.jar2uml.ParseClasses#parseClasses(java.util.jar.JarFile, java.util.Collection, java.util.Collection)}.
	 * @throws IOException 
	 * @throws CoreException 
	 */
	public void testParseClassesJarFileCollectionOfJavaClassCollectionOfJavaClass() throws CoreException, IOException {
		//
		// Retrieve Java test project
		//
		final IProject project = getProject(javatestProject);
		//
		// Create jar files in project
		//
		final IFile file = copyFileToProject(jaxbOsgiJar, project);
		final JarFile jar = jarFile(file);
		//
		// Parse classes in jar
		//
		final List<JavaClass> parsedClasses = new ArrayList<JavaClass>();
		final List<JavaClass> parsedCpClasses = new ArrayList<JavaClass>();
		final ParseClasses pc = new ParseClasses(null, null, 0);
		pc.parseClasses(jar, parsedClasses, parsedCpClasses);
		assertFalse(parsedClasses.isEmpty());
		assertTrue(parsedCpClasses.isEmpty());
		//
		// Check for duplicate class names
		//
		Set<String> classNames = new HashSet<String>();
		for (JavaClass javaClass : parsedClasses) {
			classNames.add(javaClass.getClassName());
		}
		assertEquals(parsedClasses.size(), classNames.size());
	}

	/**
	 * Test method for {@link org.eclipselabs.jar2uml.ParseClasses#parseClasses(java.util.jar.JarInputStream, java.util.Collection, java.util.Collection)}.
	 * @throws IOException 
	 * @throws CoreException 
	 */
	public void testParseClassesJarInputStreamCollectionOfJavaClassCollectionOfJavaClass() throws CoreException, IOException {
		//
		// Retrieve Java test project
		//
		final IProject project = getProject(javatestProject);
		//
		// Create jar files in project
		//
		final IFile file = copyFileToProject(jaxbOsgiJar, project);
		final JarInputStream jar = jarInputStream(file);
		//
		// Parse classes in jar
		//
		final List<JavaClass> parsedClasses = new ArrayList<JavaClass>();
		final List<JavaClass> parsedCpClasses = new ArrayList<JavaClass>();
		final ParseClasses pc = new ParseClasses(null, null, 0);
		pc.parseClasses(jar, parsedClasses, parsedCpClasses);
		assertFalse(parsedClasses.isEmpty());
		assertTrue(parsedCpClasses.isEmpty());
		//
		// Check for duplicate class names
		//
		Set<String> classNames = new HashSet<String>();
		for (JavaClass javaClass : parsedClasses) {
			classNames.add(javaClass.getClassName());
		}
		assertEquals(parsedClasses.size(), classNames.size());
	}

	/**
	 * Test method for {@link org.eclipselabs.jar2uml.ParseClasses#parseClasses(org.eclipse.core.resources.IContainer, java.util.Collection)}.
	 * @throws IOException 
	 * @throws CoreException 
	 */
	public void testParseClassesIContainerCollectionOfJavaClass() throws CoreException, IOException {
		//
		// Retrieve Java test project
		//
		final IProject project = getProject(javatestProject);
		//
		// Copy "JarToUMLTest.class" into Java project
		//
		copyClassToJavaProject(ParseClassesTest.class, project);
		//
		// Parse classes in project
		//
		final List<JavaClass> parsedClasses = new ArrayList<JavaClass>();
		final ParseClasses pc = new ParseClasses(null, null, 0);
		pc.parseClasses(project, parsedClasses);
		assertFalse(parsedClasses.isEmpty());
		//
		// Check for duplicate class names
		//
		Set<String> classNames = new HashSet<String>();
		for (JavaClass javaClass : parsedClasses) {
			classNames.add(javaClass.getClassName());
		}
		assertEquals(parsedClasses.size(), classNames.size());
	}

	/* (non-Javadoc)
	 * @see org.eclipselabs.jar2uml.test.J2UTestCase#setUp()
	 */
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		createJavaProject(javatestProject);
	}

}
