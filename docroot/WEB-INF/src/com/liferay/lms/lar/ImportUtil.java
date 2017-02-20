package com.liferay.lms.lar;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.IOUtils;

import com.liferay.lms.model.LearningActivity;
import com.liferay.lms.model.Module;
import com.liferay.lms.service.LearningActivityLocalServiceUtil;
import com.liferay.lms.service.ModuleLocalServiceUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.lar.PortletDataContext;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.util.MimeTypesUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.xml.Attribute;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.DocumentException;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portlet.documentlibrary.DuplicateFileException;
import com.liferay.portlet.documentlibrary.model.DLFolderConstants;
import com.liferay.portlet.documentlibrary.service.DLAppLocalServiceUtil;
import com.tls.lms.util.DLFolderUtil;

public class ImportUtil {
	
	private static Log log = LogFactoryUtil.getLog(ImportUtil.class);

	public static String descriptionFileParserLarToDescription(String description, FileEntry oldFile, FileEntry newFile){
		String res = description;
		
		//Precondicion
		if(oldFile == null || newFile == null){
			return res;
		}
		
		
		String target 		= "/documents/"+oldFile.getRepositoryId()+"/"+oldFile.getFolderId()+"/"+URLEncoder.encode(oldFile.getTitle())+"/"+oldFile.getUuid();
		String replacement 	= "/documents/"+newFile.getRepositoryId()+"/"+newFile.getFolderId()+"/"+URLEncoder.encode(newFile.getTitle())+"/"+newFile.getUuid();

		res = description.replace(target, replacement);
		
		if(res.equals(description)){
			log.info("   :: description         : " + description );
		}
				
		String changed = (!res.equals(description))?" changed":" not changed";
		
		log.info("   + Description file : " + newFile.getTitle() +" (" + newFile.getMimeType() + ")" + changed);
		
		return res;
	}
	
	public static FileEntry importDLFileEntry(PortletDataContext context, Element entryElement, ServiceContext serviceContext, long userId) throws PortalException, SystemException{
		if(entryElement.attributeValue("file") != null){
			
			log.info("entryElement value file-->"+entryElement.attributeValue("file"));
		
			long repositoryId = DLFolderConstants.getDataRepositoryId(context.getScopeGroupId(), DLFolderConstants.DEFAULT_PARENT_FOLDER_ID);
			long folderId=DLFolderUtil.createDLFoldersForLearningActivity(userId, repositoryId, serviceContext).getFolderId();
			
			log.info("repositoryId: " + repositoryId);
			log.info("folderId: " + folderId);
			
			String name[] = entryElement.attributeValue("file").split("/");
	
			if(name.length > 0){
				String imageName = name[name.length-1];
				InputStream input = context.getZipEntryAsInputStream(entryElement.attributeValue("file"));
			
				
				if(input != null){
					String mimeType = MimeTypesUtil.getContentType(imageName);
					log.info("mimeType: " + mimeType);
					try {								
						FileEntry newFile = DLAppLocalServiceUtil.addFileEntry(userId, repositoryId , folderId , imageName, mimeType, imageName, StringPool.BLANK, StringPool.BLANK, IOUtils.toByteArray(input), serviceContext ) ;
						if(newFile != null)log.info("newFile: " + newFile.getFileEntryId());
						return newFile;
					} catch(DuplicateFileException dfl){
						dfl.printStackTrace();
						//Si da un error de duplicado de imagen, le ponemos delante el id del modulo
						FileEntry newFile;
						try {
							Date date = new Date();
							newFile = DLAppLocalServiceUtil.addFileEntry(userId, repositoryId , folderId , date.toString() + imageName, mimeType, imageName, StringPool.BLANK, StringPool.BLANK, IOUtils.toByteArray(input), serviceContext ) ;
							return newFile;
						} catch (SystemException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (PortalException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}	
					} catch (Exception e) {
						e.printStackTrace();
						log.info("* ERROR! module file: " + e.getCause().toString());
					}	
				}
			}		
		}	
		return null;
	}
	
	public static void updateModuleIds(long groupId, HashMap<Long,Long> relationModule) throws SystemException{
		List<Module> listModules = ModuleLocalServiceUtil.findAllInGroup(groupId);
		
		for(Module module: listModules){
			if(module.getPrecedence() > 0){
				module.setPrecedence(relationModule.get(module.getPrecedence()));
				ModuleLocalServiceUtil.updateModule(module);
			}
		}
	}
	
	public static void updateActivityIds(long groupId, HashMap<Long,Long> relationActivities) throws SystemException, DocumentException, IOException{
		//Actualizamos las precedentes y el extracontent que contenga ids de actividades
		List<LearningActivity> listLearningActivity = LearningActivityLocalServiceUtil.getLearningActivitiesOfGroupAndType(groupId, 8);
		List<Attribute> listAttributes = null;
		for(LearningActivity activity: listLearningActivity){
			log.info("acitivtyid: " + activity.getActId());
			log.info("activity extra content: " + activity.getExtracontent());
			if(activity.getExtracontent() != null && !activity.getExtracontent().equals("")){
				//Extracontent	
				Document document=SAXReaderUtil.read(activity.getExtracontent());
				Element element = document.getRootElement();
				if(element != null){
					log.info("root element: " + element.toString());
					Element activities = element.element("activities");
					if(activities != null){
						log.info("element activities: " + activities.toString());
						List<Element> listElementsActivity = activities.elements();
						if(listElementsActivity != null && listElementsActivity.size() > 0){
							log.info("listElementsActivity: " + listElementsActivity.size());
							for(Element elementActivity: listElementsActivity){
								log.info("elementActivity: " + elementActivity.toString());
								Attribute attribute = elementActivity.attribute("id");
								log.info("attribute: " + attribute.toString());
								log.info("relation: " + relationActivities.get(Long.parseLong(attribute.getValue())));
								attribute.setValue(String.valueOf(relationActivities.get(Long.parseLong(attribute.getValue()))));
								log.info("attribute: " + attribute.toString());
								listAttributes = new ArrayList<Attribute>();
								listAttributes.add(attribute);
								elementActivity.setAttributes(listAttributes);
								log.info("elementActivity: " + elementActivity.toString());
							}
							activity.setExtracontent(document.formattedString());
							LearningActivityLocalServiceUtil.updateLearningActivity(activity);
						}
					}
				}
			}
		}
		
	}
}
