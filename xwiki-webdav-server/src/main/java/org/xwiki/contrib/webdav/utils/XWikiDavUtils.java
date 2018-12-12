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
package org.xwiki.contrib.webdav.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

import com.xpn.xwiki.api.Attachment;
import com.xpn.xwiki.api.Document;

/**
 * Holds all utility methods / variable for the webdav module.
 * 
 * @version $Id$.
 */
public final class XWikiDavUtils
{    
    /**
     * Path separator character.
     */
    public static final String URL_SEPARATOR = "/";            
    
    /**
     * Prefix used to indicate the beginning of a virtual grouping.
     */
    public static final String VIRTUAL_DIRECTORY_PREFIX = "_";

    /**
     * Post-fix used to indicate the beginning of a virtual grouping.
     */
    public static final String VIRTUAL_DIRECTORY_POSTFIX = VIRTUAL_DIRECTORY_PREFIX;

    /**
     * Signature used to identify an attachment url.
     */
    public static final String XWIKI_ATTACHMENT_SIGNATURE = "/bin/download/";

    /**
     * Signature used to identify a webdav url.
     */
    public static final String XWIKI_WEBDAV_SIGNATURE = "/webdav/spaces/";

    /**
     * An interface for collecting all base views.
     */
    public interface BaseViews
    {
        /**
         * Root view.
         */
        String ROOT = "root";

        /**
         * Pages view.
         */
        String PAGES = "spaces";

        /**
         * Attachments view.
         */
        String ATTACHMENTS = "attachments";

        /**
         * Home view.
         */
        String HOME = "home";

        /**
         * Orphans view.
         */
        String ORPHANS = "orphans";

        /**
         * Whatsnew view.
         */
        String WHATSNEW = "whatsnew";
    }    
    
    /**
     * Forbidden constructor.
     */
    private XWikiDavUtils()
    {

    }

    /**
     * Calculates the length of a subview (for groupings) given the total document count.
     * 
     * @param totalDocumentCount Total document count.
     * @return The calculated view name length.
     */
    public static int getSubViewNameLength(int totalDocumentCount)
    {
        // We might want to change this logic later.
        if (totalDocumentCount < 40) {
            return 0;
        } else if (totalDocumentCount < 200) {
            return 1;
        } else if (totalDocumentCount < 5000) {
            return 2;
        } else {
            return 3;
        }
    }

    /**
     * @param doc The {@link Document} having the attachment.
     * @param attachment The {@link Attachment}.
     * @return The webdav url corresponding to the attachment.
     */
    public static String getDavURL(Document doc, Attachment attachment)
    {
        final String attachmentFilePath = encode(attachment.getFilename());
        String docDownloadURL = doc.getExternalURL("download");
        String httpUrl = docDownloadURL.endsWith(URL_SEPARATOR) ? docDownloadURL + attachmentFilePath
                : docDownloadURL + URL_SEPARATOR + attachmentFilePath;
        return getDavURL(httpUrl);
    }

    /**
     * URL encode path segments in UTF-8.
     * @param pathNamePart the segment of the path to encode
     * @return the encoded part of the path
     */
    public static String encode(String pathNamePart)
    {
        try {
            return URLEncoder.encode(pathNamePart, "UTF-8").replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 not supported; this should not happen", e);
        }
    }

    public static String decode(String pathNamePart)
    {
        try {
            return URLDecoder.decode(pathNamePart, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 not supported; this should not happen", e);
        }
    }

    /**
     * @param httpUrl The http url of an attachment.
     * @return The calculated webdav url.
     */
    private static String getDavURL(String httpUrl)
    {
        // For the moment we'll only consider attachments.
        String webDAVUrl = "";
        if (httpUrl.contains(XWIKI_ATTACHMENT_SIGNATURE)) {
            String[] parts = httpUrl.split(XWIKI_ATTACHMENT_SIGNATURE, 1);
            webDAVUrl = parts[0] + XWIKI_WEBDAV_SIGNATURE + parts[1];
        }
        return webDAVUrl;
    }
}
