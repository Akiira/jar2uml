/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Dennis Wagelaar, Vrije Universiteit Brussel
 *******************************************************************************/
package be.ac.vub.jar2uml.ui;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;

import be.ac.vub.jar2uml.PublicAPIFilter;

/**
 * Import wizard to import dependencies of a Java project into a UML model in the workspace
 * @author Dennis Wagelaar <dennis.wagelaar@vub.ac.be>
 */
public class JavaProjectToUMLImportWizardPage extends AbstractJavaProjectToUMLImportWizardPage {

	protected Button allElementsBtn;
	protected Button includeInstrRefsBtn;
	protected Button includeFeaturesBtn;

	/**
	 * Creates a new JavaProjectToUMLImportDependenciesWizardPage
	 * @param pageName
	 * @param description
	 * @param selection
	 */
	public JavaProjectToUMLImportWizardPage(String pageName, String description,
			IStructuredSelection selection) {
		super(pageName, description, selection);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.dialogs.WizardNewFileCreationPage#createAdvancedControls(org.eclipse.swt.widgets.Composite)
	 */	
	protected void createAdvancedControls(Composite parent) {
		includeReferencedProjectsBtn = 
			createCheckbox(parent, "Include referenced projects and jar files in workspace", true); 
		includeFeaturesBtn = 
			createCheckbox(parent, "Include operations and attributes", true); 
		allElementsBtn = 
			createCheckbox(parent, "Include anonymous and private elements", false);
		includeInstrRefsBtn = 
			createCheckbox(parent, "Include elements referenced by bytecode instructions", false);

		includeFeaturesBtn.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (!includeFeaturesBtn.getSelection()) {
					allElementsBtn.setSelection(false);
					allElementsBtn.setEnabled(false);
					includeInstrRefsBtn.setSelection(false);
					includeInstrRefsBtn.setEnabled(false);
				} else {
					allElementsBtn.setEnabled(true);
					includeInstrRefsBtn.setEnabled(true);
				}
			}
		});
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.dialogs.WizardNewFileCreationPage#getInitialContents()
	 */
	protected InputStream getInitialContents() {
		InputStream in = super.getInitialContents();
    	jarToUML.setIncludeFeatures(includeFeaturesBtn.getSelection());
    	if (allElementsBtn.getSelection()) {
    		jarToUML.setFilter(null);
    	} else {
			jarToUML.setFilter(new PublicAPIFilter());
    	}
    	jarToUML.setIncludeInstructionReferences(includeInstrRefsBtn.getSelection());
		try {
			addAllJavaProjects(includeReferencedProjectsBtn.getSelection());
			return in;
		} catch (JavaModelException e) {
			JarToUMLPlugin.getPlugin().report(e);
			return null;
		} catch (IOException e) {
			JarToUMLPlugin.getPlugin().report(e);
			return null;
		}
	}

	/**
	 * Handles the selection of a new resource container
	 * @param event
	 */
	protected void handleSelectionEvent(Event event) {
		IPath path = getContainerFullPath();
		if (path != null) {
			setFileName(path.lastSegment() + ".uml"); //NON-NLS-1
		}
	}
}