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
package org.xwiki.contrib.webdav.resources.views;

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
import org.xwiki.contrib.webdav.resources.partial.AbstractVirtualDavView;

/**
 * This view allows to view the other entry points in the wiki that are not linked to 'Main.WebHome'. All pages that do
 * not have a parent that exists in the wiki are shown in this view.
 * 
 * @version $Id$
 */
public class OrphansView extends AbstractVirtualDavView
{
    /**
     * Logger instance.
     */
    private static final Logger logger = LoggerFactory.getLogger(OrphansView.class);

    @Override
    public XWikiDavResource decode(String[] tokens, int next) throws DavException
    {
        String nextToken = tokens[next];
        boolean last = (next == tokens.length - 1);
        if (isTempResource(nextToken)) {
            return super.decode(tokens, next);
        } else if (getContext().exists(nextToken) && !(last && getContext().isCreateOrMoveRequest())) {
            DavPage page = new DavPage();
            page.init(this, nextToken, "/" + nextToken);
            return last ? page : page.decode(tokens, next + 1);
        } else {
            throw new DavException(DavServletResponse.SC_BAD_REQUEST);
        }
    }

    @Override
    public DavResourceIterator getMembers()
    {
        List<DavResource> children = new ArrayList<DavResource>();
        String sql = "where doc.parent not in (select doc2.fullName from XWikiDocument as doc2)";
        try {
            List<String> docNames = getContext().searchDocumentsNames(sql);
            for (String docName : docNames) {
                if (getContext().hasAccess("view", docName)) {
                    DavPage page = new DavPage();
                    page.init(this, docName, "/" + docName);
                    children.add(page);
                }
            }
        } catch (DavException e) {
            logger.error("Unexpected Error : ", e);
        }
        children.addAll(getVirtualMembers());
        return new DavResourceIteratorImpl(children);
    }
}
