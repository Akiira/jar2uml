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

import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.BasicType;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.ReferenceType;
import org.apache.bcel.generic.Type;
import org.apache.bcel.verifier.structurals.UninitializedObjectType;

/**
 * Switches between various {@link Type} subclasses.
 * @author Dennis Wagelaar <dennis.wagelaar@vub.ac.be>
 */
public class TypeSwitch<T> {

	/**
	 * Switches on type and calls the correct case method. If a case method
	 * returns <code>null</code>, the case method for the superclass is invoked as well.
	 * @param type
	 * @return The object returned by the case method.
	 */
	public T doSwitch(Type type) {
		if (type instanceof BasicType) {
			T result = caseBasicType((BasicType) type);
			if (result != null) return result;
		}
		if (type instanceof ArrayType) {
			T result = caseArrayType((ArrayType) type);
			if (result != null) return result;
		}
		if (type instanceof ObjectType) {
			T result = caseObjectType((ObjectType) type);
			if (result != null) return result;
		}
		if (type instanceof UninitializedObjectType) {
			T result = caseUninitializedObjectType((UninitializedObjectType) type);
			if (result != null) return result;
		}
		if (type instanceof ReferenceType) {
			T result = caseReferenceType((ReferenceType) type);
			if (result != null) return result;
		}
		return defaultCase(type);
	}

	/**
	 * Invoked if type is a {@link BasicType}.
	 * @param type
	 * @return <code>null</code> by default.
	 */
	public T caseBasicType(BasicType type) {
		return null;
	}

	/**
	 * Invoked if type is an {@link ArrayType}.
	 * @param type
	 * @return <code>null</code> by default.
	 */
	public T caseArrayType(ArrayType type) {
		return null;
	}

	/**
	 * Invoked if type is an {@link ObjectType}.
	 * @param type
	 * @return <code>null</code> by default.
	 */
	public T caseObjectType(ObjectType type) {
		return null;
	}

	/**
	 * Invoked if type is an {@link UninitializedObjectType}.
	 * @param type
	 * @return <code>null</code> by default.
	 */
	public T caseUninitializedObjectType(UninitializedObjectType type) {
		return null;
	}

	/**
	 * Invoked if type is a {@link ReferenceType}.
	 * @param type
	 * @return <code>null</code> by default.
	 */
	public T caseReferenceType(ReferenceType type) {
		return null;
	}

	/**
	 * Invoked if type is a {@link Type}.
	 * @param type
	 * @return <code>null</code> by default.
	 */
	public T defaultCase(Type type) {
		return null;
	}

}