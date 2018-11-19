/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.webdav.resources.partial;

import org.xwiki.model.reference.EntityReference;

/**
 * A base class for all "virtual" views, which have no reference to a wiki ressource (document, space, attachment, etc)
 * by themselves.
 *
 * @since 9.5
 * @version $Id$
 *
 */
public abstract class AbstractVirtualDavView extends AbstractDavView
{

    /**
     * a virtual view has no associated entity reference.
     * 
     * @return null
     */
    @Override
    public EntityReference getReference()
    {
        return null;
    }

}