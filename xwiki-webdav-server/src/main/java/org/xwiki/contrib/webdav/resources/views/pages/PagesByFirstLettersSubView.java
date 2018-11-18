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
package org.xwiki.contrib.webdav.resources.views.pages;

import java.util.ArrayList;
import java.util.List;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceIterator;
import org.apache.jackrabbit.webdav.DavResourceIteratorImpl;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.io.InputContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.contrib.webdav.resources.XWikiDavResource;
import org.xwiki.contrib.webdav.resources.domain.DavPage;
import org.xwiki.contrib.webdav.resources.domain.DavTempFile;
import org.xwiki.contrib.webdav.resources.partial.AbstractDavView;
import org.xwiki.contrib.webdav.utils.XWikiDavUtils;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;

/**
 * The view responsible for holding a set of pages all of which begin with a particular phrase.
 * 
 * @version $Id$
 */
public class PagesByFirstLettersSubView extends AbstractDavView
{
    /**
     * Logger instance.
     */
    private static final Logger logger = LoggerFactory.getLogger(PagesByFirstLettersSubView.class);

    public SpaceReference getReference()
    {
        return ((PagesBySpaceNameSubView) parentResource).getReference();
    }

    @Override
    public void init(XWikiDavResource parent, String name, String relativePath) throws DavException
    {
        if (!(parent instanceof PagesBySpaceNameSubView)) {
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR,
                "parent should be a space view but got " + parent.getClass().getName());
        }
        super.init(parent, name, relativePath);
        if (!name.startsWith(XWikiDavUtils.VIRTUAL_DIRECTORY_PREFIX)
            || !name.endsWith(XWikiDavUtils.VIRTUAL_DIRECTORY_POSTFIX) || !name.equals(name.toUpperCase())) {
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public XWikiDavResource decode(String[] tokens, int next) throws DavException
    {
        boolean last = (next == tokens.length - 1);
        String nextToken = tokens[next];
        if (isTempResource(nextToken)) {
            return super.decode(tokens, next);
        } else if (!(last && getContext().isCreateFileRequest())) {
            DavPage page = new DavPage();
            DocumentReference docRef = new DocumentReference(nextToken, getReference());
            page.init(this, getContext().serialize(docRef), "/" + nextToken);
            return last ? page : page.decode(tokens, next + 1);
        } else {
            throw new DavException(DavServletResponse.SC_BAD_REQUEST);
        }
    }

    @Override
    public DavResourceIterator getMembers()
    {
        List<DavResource> children = new ArrayList<DavResource>();
        String filter = getDisplayName().substring(XWikiDavUtils.VIRTUAL_DIRECTORY_PREFIX.length(),
            getDisplayName().length() - XWikiDavUtils.VIRTUAL_DIRECTORY_POSTFIX.length());
        try {
            List<DocumentReference> docRefs = getContext().getChildPagesWithPrefix(getReference(), filter);
            for (DocumentReference docRef : docRefs) {
                String pageName = docRef.getName();
                DavPage page = new DavPage();
                page.init(this, getContext().serialize(docRef), "/" + pageName);
                children.add(page);
            }
        } catch (DavException e) {
            logger.error("Unexpected Error : ", e);
        }
        children.addAll(getVirtualMembers());
        return new DavResourceIteratorImpl(children);
    }

    @Override
    public void addMember(DavResource resource, InputContext inputContext) throws DavException
    {
        if (resource instanceof DavTempFile) {
            addVirtualMember(resource, inputContext);
        } else {
            // This is only a virtual grouping of pages. Delegate the request to the parent.
            getCollection().addMember(resource, inputContext);
        }
    }

    @Override
    public void removeMember(DavResource member) throws DavException
    {
        if (member instanceof DavTempFile) {
            removeVirtualMember(member);
        } else {
            // This is only a virtual grouping of pages. Delegate the request to the parent.
            getCollection().removeMember(member);
        }
    }
}
