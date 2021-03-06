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
package org.eclipselabs.jar2uml.test.data;


/**
 * Jar2UML test class. This class is not imported,
 * but is meant to be inferred by Jar2UML.
 * @author Dennis Wagelaar <dennis.wagelaar@vub.ac.be>
 */
public class A {
	
	public class AA {
		
	}

	private B.BB bbField;

	/**
	 * @return the bbField
	 */
	public B.BB getBbField() {
		return bbField;
	}

	/**
	 * @param bbField the bbField to set
	 */
	public void setBbField(B.BB bbField) {
		this.bbField = bbField;
	}
}
