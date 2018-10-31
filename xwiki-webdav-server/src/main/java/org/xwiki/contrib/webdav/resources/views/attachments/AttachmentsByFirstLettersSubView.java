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
package org.xwiki.contrib.webdav.resources.views.attachments;

import java.util.ArrayList;
import java.util.List;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceIterator;
import org.apache.jackrabbit.webdav.DavResourceIteratorImpl;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.contrib.webdav.resources.XWikiDavResource;
import org.xwiki.contrib.webdav.resources.domain.DavPage;
import org.xwiki.contrib.webdav.resources.partial.AbstractDavView;
import org.xwiki.contrib.webdav.utils.XWikiDavUtils;

/**
 * The view responsible for holding a set of pages (with attachments) all of which begin with a particular phrase.
 * 
 * @version $Id$
 */
public class AttachmentsByFirstLettersSubView extends AbstractDavView
{
    /**
     * Logger instance.
     */
    private static final Logger logger = LoggerFactory.getLogger(AttachmentsByFirstLettersSubView.class);

    @Override
    public void init(XWikiDavResource parent, String name, String relativePath) throws DavException
    {
        super.init(parent, name, relativePath);
        if (!name.startsWith(XWikiDavUtils.VIRTUAL_DIRECTORY_PREFIX)
            || !name.endsWith(XWikiDavUtils.VIRTUAL_DIRECTORY_POSTFIX) || !name.equals(name.toUpperCase())) {
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public XWikiDavResource decode(String[] tokens, int next) throws DavException
    {
        String nextToken = tokens[next];
        String pageName = getCollection().getDisplayName() + "." + nextToken;
        boolean last = (next == tokens.length - 1);
        if (isTempResource(nextToken)) {
            return super.decode(tokens, next);
        } else if (getContext().exists(pageName) && !(last && getContext().isCreateOrMoveRequest())) {
            DavPage page = new DavPage();
            page.init(this, pageName, "/" + nextToken);
            return last ? page : page.decode(tokens, next + 1);
        } else {
            throw new DavException(DavServletResponse.SC_BAD_REQUEST);
        }
    }

    @Override
    public DavResourceIterator getMembers()
    {
        List<DavResource> children = new ArrayList<DavResource>();
        String spaceName = getCollection().getDisplayName();
        String filter =
            getDisplayName().substring(XWikiDavUtils.VIRTUAL_DIRECTORY_PREFIX.length(),
                getDisplayName().length() - XWikiDavUtils.VIRTUAL_DIRECTORY_POSTFIX.length());
        try {
            String sql = ", XWikiAttachment as attach where doc.id = attach.docId and doc.web = '" + spaceName + "'";
            List<String> docNames = getContext().searchDocumentsNames(sql);
            for (String docName : docNames) {
                if (getContext().hasAccess("view", docName)) {
                    int dot = docName.lastIndexOf('.');
                    String pageName = docName.substring(dot + 1);
                    if (pageName.toUpperCase().startsWith(filter)) {
                        DavPage page = new DavPage();
                        page.init(this, docName, "/" + pageName);
                        children.add(page);
                    }
                }
            }
        } catch (DavException e) {
            logger.error("Unexpected Error : ", e);
        }
        children.addAll(getVirtualMembers());
        return new DavResourceIteratorImpl(children);
    }
}
