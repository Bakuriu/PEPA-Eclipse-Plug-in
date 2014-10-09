/*******************************************************************************
 * Copyright (c) 2006, 2009 University of Edinburgh.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the BSD Licence, which
 * accompanies this feature and can be downloaded from
 * http://groups.inf.ed.ac.uk/pepa/update/licence.txt
 *******************************************************************************/
package uk.ac.ed.inf.pepa.eclipse.ui.view.utilisationview;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;

import uk.ac.ed.inf.pepa.ctmc.PopulationLevelResult;

public class PopulationTableProvider extends LabelProvider implements
		ITableLabelProvider {

	public Image getColumnImage(Object element, int columnIndex) {
		return null;
	}

	public String getColumnText(Object element, int columnIndex) {
		if (!(element instanceof PopulationLevelResult))
			return null;
		else {
			if (columnIndex == 0)
				return ((PopulationLevelResult) element).getName();
			if (columnIndex == 1)
				return Double.toString((((PopulationLevelResult) element).getMean()));
			return null;
		}
	}

}
