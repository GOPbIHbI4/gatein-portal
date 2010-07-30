/**
 * Copyright (C) 2009 eXo Platform SAS.
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

package org.exoplatform.upload;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.PortalContainerInfo;
import org.gatein.common.logging.Logger;
import org.gatein.common.logging.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by The eXo Platform SARL
 * Author : Tuan Nguyen
 *          tuan.nguyen@exoplatform.com
 * Dec 8, 2006  
 */
public class UploadService
{

   /** . */
   private static final Logger log = LoggerFactory.getLogger(UploadService.class);

   private Map<String, UploadResource> uploadResources = new LinkedHashMap<String, UploadResource>();

   private String uploadLocation_;

   private int defaultUploadLimitMB_;

   private Map<String, Integer> uploadLimitsMB_ = new LinkedHashMap<String, Integer>();

   public UploadService(PortalContainerInfo pinfo, InitParams params) throws Exception
   {
      String tmpDir = System.getProperty("java.io.tmpdir");
      if (params == null || params.getValueParam("upload.limit.size") == null)
         defaultUploadLimitMB_ = 0; // 0 means unlimited
      else
         defaultUploadLimitMB_ = Integer.parseInt(params.getValueParam("upload.limit.size").getValue());
      uploadLocation_ = tmpDir + "/" + pinfo.getContainerName() + "/eXoUpload";
      File uploadDir = new File(uploadLocation_);
      if (!uploadDir.exists())
         uploadDir.mkdirs();
   }

   /**
    * Create UploadResource for HttpServletRequest.
    * If Upload size greater than limit upload size, do not create UploadResource
    * @param request
    *             the webapp's {@link javax.servlet.http.HttpServletRequest}
    * @throws IOException 
    *             any io exception
    */
   public void createUploadResource(HttpServletRequest request) throws IOException
   {
      String uploadId = request.getParameter("uploadId");
      // by default, use the limit set in the service
      //int limitMB = defaultUploadLimitMB_;
      // if the limit is set in the request (specific for this upload) then use this value instead of the default one
      //if (uploadLimitsMB_.containsKey(uploadId)) limitMB = uploadLimitsMB_.get(uploadId).intValue() ;
      int limitMB = uploadLimitsMB_.get(uploadId).intValue();

      UploadResource upResource = new UploadResource(uploadId);
      RequestStreamReader reader = new RequestStreamReader(upResource);
      int estimatedSizeMB = (request.getContentLength() / 1024) / 1024;
      if (limitMB > 0 && estimatedSizeMB > limitMB)
      { // a limit set to 0 means unlimited
         upResource.setStatus(UploadResource.FAILED_STATUS);
         //upResource.setLimitMB(limitMB);
         uploadResources.put(uploadId, upResource);
         log.debug("Upload cancelled because file bigger than size limit : " + estimatedSizeMB + " MB > "
            + limitMB + " MB");
         //    	WebuiRequestContext ctx = WebuiRequestContext.getCurrentInstance();
         //        UIApplication uiApp = ctx.getUIApplication();
         //        uiApp.addMessage(new ApplicationMessage("The file must be < "+limitMB+" MB.", null, ApplicationMessage.WARNING));
         return;
      }
      // TODO : display error message, terminate upload correctly

      String headerEncoding = request.getCharacterEncoding();
      Map<String, String> headers = reader.parseHeaders(request.getInputStream(), headerEncoding);

      String fileName = reader.getFileName(headers);
      if (fileName == null)
         fileName = uploadId;
      fileName = fileName.substring(fileName.lastIndexOf('\\') + 1);

      upResource.setFileName(fileName);
      upResource.setMimeType(headers.get(RequestStreamReader.CONTENT_TYPE));
      upResource.setStoreLocation(uploadLocation_ + "/" + uploadId + "." + fileName);
      upResource.setEstimatedSize(request.getContentLength());

      uploadResources.put(upResource.getUploadId(), upResource);

      File fileStore = new File(upResource.getStoreLocation());
      if (!fileStore.exists())
         fileStore.createNewFile();
      FileOutputStream output = new FileOutputStream(fileStore);
      reader.readBodyData(request, output);

      if (upResource.getStatus() == UploadResource.UPLOADING_STATUS)
      {
         upResource.setStatus(UploadResource.UPLOADED_STATUS);
         return;
      }

      uploadResources.remove(uploadId);
      fileStore.delete();
   }

   /**
    * Create UploadResource for uploadId.
    * If Upload size greater than limit upload size, do not create UploadResource.
    * @param uploadId
    *          the uploadId will be use to create UploadResource
    * @param encoding
    *           type of encode
    * @param contentType
    *          type of upload content
    * @param contentLength
    *          size of upload content
    * @param inputStream
    *          java.io.InputStream
    * @throws Exception
    */
   public void createUploadResource(String uploadId, String encoding, String contentType, double contentLength,
      InputStream inputStream) throws Exception
   {
      UploadResource upResource = new UploadResource(uploadId);
      RequestStreamReader reader = new RequestStreamReader(upResource);
      int limitMB = uploadLimitsMB_.get(uploadId).intValue();
      int estimatedSizeMB = (int)contentLength / 1024 / 1024;
      if (limitMB > 0 && estimatedSizeMB > limitMB)
      { // a limit set to 0 means unlimited
         upResource.setStatus(UploadResource.FAILED_STATUS);
         uploadResources.put(uploadId, upResource);
         log.debug("Upload cancelled because file bigger than size limit : " + estimatedSizeMB + " MB > "
            + limitMB + " MB");
         return;
      }
      Map<String, String> headers = reader.parseHeaders(inputStream, encoding);

      String fileName = reader.getFileName(headers);
      if (fileName == null)
         fileName = uploadId;
      fileName = fileName.substring(fileName.lastIndexOf('\\') + 1);

      upResource.setFileName(fileName);
      upResource.setMimeType(headers.get(RequestStreamReader.CONTENT_TYPE));
      upResource.setStoreLocation(uploadLocation_ + "/" + uploadId + "." + fileName);
      upResource.setEstimatedSize(contentLength);
      uploadResources.put(upResource.getUploadId(), upResource);
      File fileStore = new File(upResource.getStoreLocation());
      if (!fileStore.exists())
         fileStore.createNewFile();
      FileOutputStream output = new FileOutputStream(fileStore);
      reader.readBodyData(inputStream, contentType, output);

      if (upResource.getStatus() == UploadResource.UPLOADING_STATUS)
      {
         upResource.setStatus(UploadResource.UPLOADED_STATUS);
         return;
      }

      uploadResources.remove(uploadId);
      fileStore.delete();
   }

   /**
    * Get UploadResource by uploadId
    * @param uploadId
    *          uploadId of UploadResource
    * @return org.exoplatform.upload.UploadResource of uploadId
    */
   public UploadResource getUploadResource(String uploadId)
   {//throws Exception 
      UploadResource upResource = uploadResources.get(uploadId);
      return upResource;
   }

   /**
    * Remove UploadResource by uploadId, Delete temp file of UploadResource.
    * If uploadId is null or UploadResource is null or Store Location of UploadResource is null, do nothing
    * @param uploadId
    *          uploadId of UploadResource will be removed
    */
   public void removeUpload(String uploadId)
   {
      if (uploadId == null)
         return;
      UploadResource upResource = uploadResources.get(uploadId);
      if (upResource == null)
         return;
      if (upResource.getStoreLocation() == null)
         return;
      File file = new File(upResource.getStoreLocation());
      file.delete();
      uploadResources.remove(uploadId);
      //uploadLimitsMB_.remove(uploadId);
   }

   /**
    * Registry upload limit size for uploadLimitsMB_.
    * If limitMB is null, defaultUploadLimitMB_ will be registried
    * @param uploadId
    * @param limitMB
    *          upload limit size
    */
   public void addUploadLimit(String uploadId, Integer limitMB)
   {
      if (limitMB == null)
         uploadLimitsMB_.put(uploadId, Integer.valueOf(defaultUploadLimitMB_));
      else
         uploadLimitsMB_.put(uploadId, limitMB);
   }

   /**
    * Get all upload limit sizes
    * @return 
    *     all upload limit sizes
    */
   public Map<String, Integer> getUploadLimitsMB()
   {
      return uploadLimitsMB_;
   }
}