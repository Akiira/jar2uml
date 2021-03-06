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
package org.eclipselabs.jar2uml;

import org.apache.bcel.classfile.AccessFlags;
import org.apache.bcel.classfile.JavaClass;

/**
 * Includes only named public/protected elements. Includes all
 * named classes, as they may be subclassed by public/protected
 * named classes.
 * @author Dennis Wagelaar <dennis.wagelaar@vub.ac.be>
 */
public class PublicAPIFilter implements Filter {

	/*
	 * (non-Javadoc)
	 * @see org.eclipselabs.jar2uml.Filter#filter(java.lang.String)
	 */
	public boolean filter(String expression) {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipselabs.jar2uml.Filter#filter(org.apache.bcel.classfile.JavaClass)
	 */
	public boolean filter(JavaClass javaClass) {
		return JarToUML.isNamedClass(javaClass);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipselabs.jar2uml.Filter#filter(org.apache.bcel.classfile.AccessFlags)
	 */
	public boolean filter(AccessFlags flags) {
		return (flags.isPublic() || flags.isProtected());
	}

}
