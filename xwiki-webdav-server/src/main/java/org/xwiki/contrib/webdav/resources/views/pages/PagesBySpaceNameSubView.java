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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import org.xwiki.contrib.webdav.resources.partial.AbstractDavView;
import org.xwiki.contrib.webdav.utils.XWikiDavUtils;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * This view groups all pages according to their space name.
 * 
 * @version $Id$
 */
public class PagesBySpaceNameSubView extends AbstractDavView
{
    /**
     * Logger instance.
     */
    private static final Logger logger = LoggerFactory.getLogger(PagesBySpaceNameSubView.class);

    @Override
    public XWikiDavResource decode(String[] tokens, int next) throws DavException
    {
        String nextToken = tokens[next];
        boolean last = (next == tokens.length - 1);
        XWikiDavResource resource = null;
        if (isTempResource(nextToken)) {
            return super.decode(tokens, next);
        } else if ((nextToken.startsWith(XWikiDavUtils.VIRTUAL_DIRECTORY_PREFIX) && nextToken
            .endsWith(XWikiDavUtils.VIRTUAL_DIRECTORY_POSTFIX))
            && !(last && getContext().isCreateOrMoveRequest())) {
            resource = new PagesByFirstLettersSubView();
            resource.init(this, nextToken.toUpperCase(), "/" + nextToken.toUpperCase());
        } else if (getContext().isCreateCollectionRequest()
            || getContext().documentExists(new DocumentReference(nextToken, getReference()))) {
            resource = new DavPage();
            DocumentReference docRef = new DocumentReference(nextToken, getReference());
            resource.init(this, getContext().serialize(docRef), "/" + nextToken);
        } else if (getContext().spaceExists( new SpaceReference(nextToken, getReference()))) {
            resource = new PagesBySpaceNameSubView();
            resource.init(this, nextToken, "/" + nextToken);
        } else if (nextToken.startsWith(this.name + ".") && getContext().documentExists(getContext().getDocumentReference(nextToken))) {
            // For compatibility with FoXWiki
            resource = new DavPage();
            resource.init(this, nextToken, "/" + nextToken);
        } else {
            // maybe throw "NOT_FOUND" here?
            throw new DavException(DavServletResponse.SC_BAD_REQUEST);
        }
        return last ? resource : resource.decode(tokens, next + 1);
    }

    @Override
    public boolean exists()
    {
        return getContext().spaceExists(getReference());
    }

    @Override
    public SpaceReference getReference()
    {
        return new SpaceReference(this.name, parentResource.getReference());
    }

    @Override
    public DavResourceIterator getMembers()
    {
        List<DavResource> children = new ArrayList<DavResource>();
        try {
            List<String> childSpaces = getContext().getChildSpaces(getReference());
            for(String childSpace : childSpaces) {
                PagesBySpaceNameSubView childSpaceView = new PagesBySpaceNameSubView();
                childSpaceView.init(this, childSpace, "/" + childSpace);
                children.add(childSpaceView);
            }

            List<DocumentReference> docRefs = getContext().getChildPages(getReference());
            Set<String> subViewNames = new HashSet<String>();
            int subViewNameLength = XWikiDavUtils.getSubViewNameLength(docRefs.size());
            for (DocumentReference docRef : docRefs) {
                logger.debug("check for page [{}] as {}", docRef, docRef.getName() );
                if (getContext().hasAccess("view", docRef)) {
                    String pageName = docRef.getName();
                    if (subViewNameLength == 0) {
                        DavPage page = new DavPage();
                        page.init(this, getContext().serialize(docRef), "/" + pageName);
                        children.add(page);
                    } else {
                        if (subViewNameLength < pageName.length()) {
                            subViewNames.add(pageName.substring(0, subViewNameLength).toUpperCase());
                        } else {
                            // This is not good.
                            subViewNames.add(pageName.toUpperCase());
                        }
                    }
                }
            }

            for (String subViewName : subViewNames) {
                try {
                    String modName =
                        XWikiDavUtils.VIRTUAL_DIRECTORY_PREFIX + subViewName + XWikiDavUtils.VIRTUAL_DIRECTORY_POSTFIX;
                    PagesByFirstLettersSubView subView = new PagesByFirstLettersSubView();
                    subView.init(this, modName, "/" + modName);
                    children.add(subView);
                } catch (DavException e) {
                    logger.error("Unexpected Error : ", e);
                }
            }
        } catch (DavException ex) {
            logger.error("Unexpected Error : ", ex);
        }
        children.addAll(getVirtualMembers());
        return new DavResourceIteratorImpl(children);
    }

    @Override
    public void addMember(DavResource resource, InputContext inputContext) throws DavException
    {
        if (resource instanceof DavPage) {
            DocumentReference newDocRef = getContext().getDocumentReference(resource.getDisplayName());
            if (getContext().hasAccess("edit", newDocRef)) {
                XWikiDocument childDoc = getContext().getDocument(newDocRef);
                childDoc.setContent("This page was created through the WebDAV interface.");
                getContext().saveDocument(childDoc);
            }
        } else {
            super.addMember(resource, inputContext);
        }
    }

    @Override
    public void removeMember(DavResource member) throws DavException
    {
        XWikiDavResource davResource = (XWikiDavResource) member;
        if (davResource instanceof DavPage) {
            DocumentReference memberRef = getContext().getDocumentReference(davResource.getDisplayName());
            getContext().checkAccess("delete", memberRef);
            XWikiDocument childDoc = getContext().getDocument(memberRef);
            if (!childDoc.isNew()) {
                getContext().deleteDocument(childDoc);
            }
        } else if (member instanceof PagesByFirstLettersSubView) {
            // We are going to force a recursive delete.
            String filter =
                member.getDisplayName().substring(XWikiDavUtils.VIRTUAL_DIRECTORY_PREFIX.length(),
                    member.getDisplayName().length() - XWikiDavUtils.VIRTUAL_DIRECTORY_POSTFIX.length());
            List<DocumentReference> docRefs = getContext().getChildPagesWithPrefix(getReference(), filter);
            // Verify delete rights on all the documents to be removed.
            for (DocumentReference docRef : docRefs) {
                getContext().checkAccess("delete", docRef);
            }
            // Delete the documents.
            for (DocumentReference docRef : docRefs) {
                getContext().deleteDocument(getContext().getDocument(docRef));
            }
        } else {
            super.removeMember(member);
        }
        davResource.clearCache();
    }

    @Override
    public void move(DavResource destination) throws DavException
    {
        // We only support rename operation for the moment.
        if (destination instanceof PagesBySpaceNameSubView) {
            PagesBySpaceNameSubView dSpace = (PagesBySpaceNameSubView) destination;
            SpaceReference dSpaceRef = dSpace.getReference();
            if (!dSpace.exists()) {
                // Now check whether this is a rename operation.
                if (getCollection().equals(dSpace.getCollection())) {
                    List<DocumentReference> childPages = getContext().getChildPages(getReference());

                    // To rename an entire space, user should have edit rights on all the
                    // documents in the current space and delete rights on all the documents that
                    // will be replaced (if they exist).
                    SpaceReference space = dSpace.getReference();
                    getContext().checkAccess("edit", space);
                    for (DocumentReference docRef : childPages) {
                        getContext().checkAccess("overwrite", docRef);
                    }
                    for (DocumentReference docRef : childPages) {
                        XWikiDocument doc = getContext().getDocument(docRef);
                        DocumentReference newDocRef = new DocumentReference(docRef.getName(), dSpaceRef);
                        getContext().renameDocument(doc, newDocRef);
                    }
                    // FIXME: same for the subspaces!
                    // maybe use rename job instead (?)

                } else {
                    // Actual moves (perhaps from one view to another) is not
                    // allowed.
                    throw new DavException(DavServletResponse.SC_BAD_REQUEST);
                }
            } else {
                throw new DavException(DavServletResponse.SC_BAD_REQUEST);
            }
        } else {
            throw new DavException(DavServletResponse.SC_BAD_REQUEST);
        }
        clearCache();
    }
}
