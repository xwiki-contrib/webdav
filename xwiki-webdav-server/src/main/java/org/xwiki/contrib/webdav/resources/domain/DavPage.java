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
package org.xwiki.contrib.webdav.resources.domain;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.jackrabbit.server.io.IOUtil;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceIterator;
import org.apache.jackrabbit.webdav.DavResourceIteratorImpl;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.io.InputContext;
import org.apache.jackrabbit.webdav.io.OutputContext;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.contrib.webdav.resources.XWikiDavResource;
import org.xwiki.contrib.webdav.resources.partial.AbstractDavResource;
import org.xwiki.model.reference.DocumentReference;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * The collection resource which represents a page {@link XWikiDocument} of XWiki.
 * 
 * @version $Id$
 */
public class DavPage extends AbstractDavResource
{
    /**
     * Logger instance.
     */
    private static final Logger logger = LoggerFactory.getLogger(DavPage.class);

    /**
     * The {@link XWikiDocument} represented by this resource.
     */
    private XWikiDocument doc;

    /**
     * The {@link DocumentReference} for the document referenced by this resource.
     */
    private DocumentReference docRef;

    @Override
    public void init(XWikiDavResource parent, String name, String relativePath) throws DavException
    {
        super.init(parent, name, relativePath);

        // we should get a full path here
        docRef = getContext().getDocumentReference(this.name);
        if (docRef.getLastSpaceReference() == null) {
            throw new DavException(DavServletResponse.SC_BAD_REQUEST);
        }

        this.doc = getContext().getDocument(docRef);
        String timeStamp = DavConstants.creationDateFormat.format(doc.getCreationDate());
        getProperties().add(new DefaultDavProperty(DavPropertyName.CREATIONDATE, timeStamp));
        timeStamp = DavConstants.modificationDateFormat.format(doc.getContentUpdateDate());
        getProperties().add(new DefaultDavProperty(DavPropertyName.GETLASTMODIFIED, timeStamp));
        getProperties().add(new DefaultDavProperty(DavPropertyName.GETETAG, timeStamp));
        getProperties().add(new DefaultDavProperty(DavPropertyName.GETCONTENTTYPE, "text/directory"));
        getProperties().add(new DefaultDavProperty(DavPropertyName.GETCONTENTLANGUAGE, doc.getLocale().toString()));
        getProperties().add(new DefaultDavProperty(DavPropertyName.GETCONTENTLENGTH, 0));
    }

    @Override
    public XWikiDavResource decode(String[] tokens, int next) throws DavException
    {
        String nextToken = tokens[next];
        boolean last = (next == tokens.length - 1);
        XWikiDavResource resource = null;
        String relativePath = "/" + nextToken;
        if (isTempResource(nextToken)) {
            return super.decode(tokens, next);
        } else if (nextToken.equals(DavWikiFile.WIKI_TXT) || nextToken.equals(DavWikiFile.WIKI_XML)) {
            resource = new DavWikiFile();
            resource.init(this, nextToken, relativePath);
        } else if (doc.getAttachment(nextToken) != null || (last && getContext().isCreateFileRequest())
            || (last && getContext().isMoveAttachmentRequest(doc))) {
            resource = new DavAttachment();
            resource.init(this, nextToken, relativePath);
        } else {
            // children pages: if they are in the same space as the current page, we get a relative path, otherwise a
            // full one. try to distinguish between both cases
            DocumentReference nextDocRef = getContext().getDocumentReference(nextToken);
            if (nextDocRef.getLastSpaceReference() == null) {
                nextDocRef = new DocumentReference(nextDocRef.getName(), docRef.getLastSpaceReference());
            }
            resource = new DavPage();
            resource.init(this, getContext().serialize(nextDocRef), relativePath);
        }
        return last ? resource : resource.decode(tokens, next + 1);
    }

    @Override
    public boolean exists()
    {
        return !doc.isNew();
    }

    @Override
    public DocumentReference getReference()
    {
        return docRef;
    }

    /**
     * the members of a page are both its subpages in the old parent - child relation and the attachments of that page.
     */
    @Override
    public DavResourceIterator getMembers()
    {
        // Protect against direct url referencing.
        List<DavResource> children = new ArrayList<DavResource>();
        if (!getContext().hasAccess("view", this.docRef)) {
            return new DavResourceIteratorImpl(children);
        }
        try {
            List<DocumentReference> childReferences = getContext().getChildPages(docRef);
            for (DocumentReference childReference : childReferences) {
                String childDocName = getContext().serialize(childReference);
                if (!createsCycle(childDocName) && getContext().hasAccess("view", childReference)) {
                    DavPage page = new DavPage();

                    if (childReference.getLastSpaceReference().equals(this.docRef.getLastSpaceReference())) {
                        page.init(this, childDocName, "/" + childReference.getName());
                    } else {
                        page.init(this, childDocName, "/" + childDocName);
                    }
                    children.add(page);
                }
            }
            List<String> filenames = getContext().getAttachmentsForPage(docRef);
            for (String filename : filenames) {
                DavAttachment attachment = new DavAttachment();
                attachment.init(this, filename, "/" + filename);
                children.add(attachment);
            }
            children.addAll(getVirtualMembers());
        } catch (DavException e) {
            logger.error("Unexpected Error : ", e);
        }
        return new DavResourceIteratorImpl(children);
    }

    @Override
    public void addMember(DavResource resource, InputContext inputContext) throws DavException
    {
        getContext().checkAccess("edit", this.docRef);
        boolean isFile = (inputContext.getInputStream() != null);
        if (resource instanceof DavTempFile) {
            addVirtualMember(resource, inputContext);
        } else if (resource instanceof DavPage) {
            DocumentReference pName = ((DavPage) resource).getReference();
            getContext().checkAccess("edit", pName);
            XWikiDocument childDoc = getContext().getDocument(pName);
            childDoc.setContent("This page was created through the WebDAV interface.");
            childDoc.setParentReference(this.docRef);
            getContext().saveDocument(childDoc);
        } else if (isFile) {
            String fName = resource.getDisplayName();
            if (fName.equals(DavWikiFile.WIKI_TXT)) {
                String data = getContext().getFileContentAsString(inputContext.getInputStream());
                doc.setContent(data);
                getContext().saveDocument(doc);
            } else if (fName.equals(DavWikiFile.WIKI_XML)) {
                throw new DavException(DavServletResponse.SC_METHOD_NOT_ALLOWED);
            } else {
                try (InputStream in = inputContext.getInputStream()) {
                    getContext().addAttachment(doc, in, fName, null);
                } catch (IOException ioe) {
                    throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR, ioe);
                }
            }
        } else {
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void removeMember(DavResource member) throws DavException
    {
        getContext().checkAccess("edit", docRef);
        XWikiDavResource dResource = (XWikiDavResource) member;
        String mName = dResource.getDisplayName();
        if (dResource instanceof DavTempFile) {
            removeVirtualMember(dResource);
        } else if (dResource instanceof DavWikiFile) {
            getContext().checkAccess("delete", docRef);
            removeVirtualMember(dResource);
        } else if (dResource instanceof DavAttachment) {
            getContext().deleteAttachment(doc.getAttachment(mName));
        } else if (dResource instanceof DavPage) {
            XWikiDocument childDoc = getContext().getDocument(docRef);
            getContext().checkAccess("delete", childDoc.getDocumentReference());
            if (!childDoc.isNew()) {
                getContext().deleteDocument(childDoc);
            }
        } else {
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        dResource.clearCache();
    }

    @Override
    public void move(DavResource destination) throws DavException
    {
        // Renaming a page requires edit rights on the current document, overwrite rights on the
        // target document and edit rights on all the children of current document.
        getContext().checkAccess("edit", this.docRef);
        if (destination instanceof DavPage) {
            DavPage dPage = (DavPage) destination;
            XWikiDocument dDoc = dPage.getDocument();
            // XXX: why this check?
            // do we really need the new parent space to exist?
            if (getContext().spaceExists(dPage.getReference().getLastSpaceReference())) {
                DocumentReference newDocName = dDoc.getDocumentReference();
                List<DocumentReference> childDocNames = getContext().getChildPages(docRef);
                // Validate access rights for the destination page.
                getContext().checkAccess("overwrite", newDocName);
                // Validate access rights for all the child pages.
                for (DocumentReference childDocName : childDocNames) {
                    getContext().checkAccess("edit", childDocName);
                }
                getContext().renameDocument(doc, newDocName);
                for (DocumentReference childDocName : childDocNames) {
                    XWikiDocument childDoc = getContext().getDocument(childDocName);
                    childDoc.setParentReference(newDocName);
                    getContext().saveDocument(childDoc);
                }
            }
        } else {
            throw new DavException(DavServletResponse.SC_BAD_REQUEST);
        }
        clearCache();
    }

    @Override
    public List<XWikiDavResource> getInitMembers()
    {
        List<XWikiDavResource> initialMembers = new ArrayList<XWikiDavResource>();
        try {
            DavWikiFile wikiText = new DavWikiFile();
            wikiText.init(this, DavWikiFile.WIKI_TXT, "/" + DavWikiFile.WIKI_TXT);
            initialMembers.add(wikiText);
            DavWikiFile wikiXml = new DavWikiFile();
            wikiXml.init(this, DavWikiFile.WIKI_XML, "/" + DavWikiFile.WIKI_XML);
            initialMembers.add(wikiXml);
        } catch (DavException ex) {
            logger.error("Error while initializing members.", ex);
        }
        return initialMembers;
    }

    @Override
    public boolean isCollection()
    {
        return true;
    }

    @Override
    public void spool(OutputContext outputContext) throws IOException
    {
        throw new IOException("Collection resources can't be spooled");
    }

    @Override
    public long getModificationTime()
    {
        if (exists()) {
            return doc.getContentUpdateDate().getTime();
        }
        return IOUtil.UNDEFINED_TIME;
    }

    /**
     * @return The document represented by this resource.
     */
    public XWikiDocument getDocument()
    {
        return this.doc;
    }

    /**
     * Utility method to verify that a member of this resource doesn't give rise to a cycle.
     * 
     * @param childDocName
     *            Name of the want-to-be-member resource.
     * @return True if the childPageName has occured before, false otherwise.
     */
    public boolean createsCycle(String childDocName)
    {
        DavResource ancestor = this;
        while (ancestor instanceof DavPage && ancestor != null) {
            if (ancestor.getDisplayName().equals(childDocName)) {
                return true;
            }
            ancestor = ancestor.getCollection();
        }
        return false;
    }
}
