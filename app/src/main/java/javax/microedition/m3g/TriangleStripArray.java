/*
 * Copyright (c) 2003 Nokia Corporation and/or its subsidiary(-ies).
 * All rights reserved.
 * This component and the accompanying materials are made available
 * under the terms of "Eclipse Public License v1.0"
 * which accompanies this distribution, and is available
 * at the URL "http://www.eclipse.org/legal/epl-v10.html".
 *
 * Initial Contributors:
 * Nokia Corporation - initial contribution.
 *
 * Contributors:
 *
 * Description:
 *
 */

package javax.microedition.m3g;

public class TriangleStripArray extends IndexBuffer {
	//------------------------------------------------------------------
	// Constructors
	//------------------------------------------------------------------

	public TriangleStripArray(int firstIndex, final int[] stripLengths) {
		super(_createImplicit(Interface.getHandle(),
				firstIndex,
				stripLengths));
		this.stripLengths = stripLengths.clone();
		this.firstIndex = firstIndex;
		this.implicit = true;
	}

	public TriangleStripArray(final int[] indices, final int[] stripLengths) {
		super(_createExplicit(Interface.getHandle(), indices, stripLengths));
		this.indices = indices.clone();
		this.stripLengths = stripLengths.clone();
		this.implicit = false;
	}

	// Kept for snapshot replay: the API has no stripLengths getter, and the
	// caller may reuse/mutate the arrays after construction — clone eagerly.
	// implicit ctor records firstIndex so revive can replay the same call.
	// Explicit ctor records the raw indices: getIndices() only exposes the
	// expanded triangle list, which is NOT a valid strip stream to re-feed.
	int[] indices;
	int[] stripLengths;
	int firstIndex;
	boolean implicit;

	TriangleStripArray(long handle) {
		super(handle);
	}

	// M3G 1.1 Maintenance release getters

	@Override
	public int getIndexCount() {
		return _getIndexCount(handle);
	}

	@Override
	public void getIndices(int[] indices) {
		if (indices.length < _getIndexCount(handle)) {
			throw new IllegalArgumentException();
		}
		_getIndices(handle, indices);
	}

	//------------------------------------------------------------------
	// Private methods
	//------------------------------------------------------------------

	// Native methods
	private native static long _createImplicit(long hInterface,
											  int first,
											  final int[] lengths);

	private native static long _createExplicit(long hInterface,
											  final int[] indices,
											  final int[] lengths);

	// M3G 1.1 Maintenance release getters
	private native static int _getIndexCount(long handle);

	private native static void _getIndices(long handle, int[] indices);

}
