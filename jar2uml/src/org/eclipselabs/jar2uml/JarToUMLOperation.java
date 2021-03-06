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
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

/**
 * Introduces functionality for general {@link JarToUML} operations. 
 * @author Dennis Wagelaar <dennis.wagelaar@vub.ac.be>
 */
public abstract class JarToUMLOperation implements Cancellable {

	private Filter filter;
	private IProgressMonitor monitor;

	/**
	 * Creates a new {@link JarToUMLOperation}.
	 * @param filter A filter to apply to model operations.
	 * @param monitor A progress monitor to check for end user cancellation.
	 * @param ticks amount of ticks this task will add to the progress monitor
	 */
	public JarToUMLOperation(Filter filter, IProgressMonitor monitor, int ticks) {
		super();
		setFilter(filter);
		if (monitor != null) {
			setMonitor(new SubProgressMonitor(monitor, ticks));
		}
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
	}

	/**
	 * @param expr The Java Jar file entry expression.
	 * @return True if the Jar entry should be included in the UML model.
	 */
	protected boolean filter(final String expr) {
		final Filter filter = getFilter();
		return (filter == null) || (filter.filter(expr));
	}

	/**
	 * @param javaClass The parsed Java class of interface.
	 * @return True if the class/interface should be included in the UML model.
	 */
	protected boolean filter(final JavaClass javaClass) {
		final Filter filter = getFilter();
		return (filter == null) || (filter.filter(javaClass));
	}

	/**
	 * @param flags The access modifier flags (public/protected/private) of a Java element.
	 * @return True if the given access modifier level should be included in the UML model.
	 */
	protected boolean filter(final AccessFlags flags) {
		final Filter filter = getFilter();
		return (filter == null) || (filter.filter(flags));
	}

	/**
	 * Handles cancelled progress monitor
	 * @throws OperationCanceledException
	 */
	public void checkCancelled() throws OperationCanceledException {
		final IProgressMonitor monitor = getMonitor();
		if ((monitor != null) && monitor.isCanceled()) {
			throw new OperationCanceledException(JarToUMLResources.getString("operationCancelledByUser")); //$NON-NLS-1$
		}
	}

	/**
	 * Starts a new task, if the monitor is set.
	 * @param taskName
	 * @param work
	 */
	public void beginTask(final String taskName, final int work) {
		final IProgressMonitor monitor = getMonitor();
		if (monitor != null) {
			monitor.beginTask(taskName, work);
		}
	}

	/**
	 * Increases the monitor progress by 1 and andles cancelled progress monitor,
	 * if monitor is set.
	 * @throws OperationCanceledException
	 */
	public void worked() throws OperationCanceledException {
		final IProgressMonitor monitor = getMonitor();
		if (monitor != null) {
			monitor.worked(1);
			if (monitor.isCanceled()) {
				throw new OperationCanceledException(JarToUMLResources.getString("operationCancelledByUser")); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Logs the skipping of javaClass.
	 * @param javaClass
	 */
	protected void logSkippedFiltered(JavaClass javaClass) {
		JarToUMLResources.logger.fine(String.format(
				JarToUMLResources.getString("JarToUMLOperation.skippedFiltered"), 
				javaClass.getClassName())); //$NON-NLS-1$
	}

	/**
	 * @return the monitor
	 */
	public IProgressMonitor getMonitor() {
		return monitor;
	}

	/**
	 * @param monitor the monitor to set
	 */
	public void setMonitor(IProgressMonitor monitor) {
		this.monitor = monitor;
	}

}