package com.liferay.lms;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.apache.commons.io.IOUtils;

import com.liferay.counter.model.Counter;
import com.liferay.counter.service.CounterLocalServiceUtil;
import com.liferay.lms.learningactivity.LearningActivityTypeRegistry;
import com.liferay.lms.model.Course;
import com.liferay.lms.model.CourseCompetence;
import com.liferay.lms.model.LearningActivity;
import com.liferay.lms.model.LmsPrefs;
import com.liferay.lms.model.Module;
import com.liferay.lms.model.TestAnswer;
import com.liferay.lms.model.TestQuestion;
import com.liferay.lms.service.CourseCompetenceLocalServiceUtil;
import com.liferay.lms.service.CourseLocalServiceUtil;
import com.liferay.lms.service.LearningActivityLocalServiceUtil;
import com.liferay.lms.service.LmsPrefsLocalServiceUtil;
import com.liferay.lms.service.ModuleLocalServiceUtil;
import com.liferay.lms.service.TestAnswerLocalServiceUtil;
import com.liferay.lms.service.TestQuestionLocalServiceUtil;
import com.liferay.portal.DuplicateGroupException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageListener;
import com.liferay.portal.kernel.messaging.MessageListenerException;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.repository.model.Folder;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.DocumentException;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.ResourceAction;
import com.liferay.portal.model.ResourceConstants;
import com.liferay.portal.model.ResourcePermission;
import com.liferay.portal.model.Role;
import com.liferay.portal.model.RoleConstants;
import com.liferay.portal.model.User;
import com.liferay.portal.security.auth.PrincipalThreadLocal;
import com.liferay.portal.security.permission.ActionKeys;
import com.liferay.portal.security.permission.PermissionChecker;
import com.liferay.portal.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.security.permission.PermissionThreadLocal;
import com.liferay.portal.service.GroupLocalServiceUtil;
import com.liferay.portal.service.ResourceActionLocalServiceUtil;
import com.liferay.portal.service.ResourcePermissionLocalServiceUtil;
import com.liferay.portal.service.RoleLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.UserGroupRoleLocalServiceUtil;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.theme.ThemeDisplay;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portlet.announcements.model.AnnouncementsEntry;
import com.liferay.portlet.announcements.model.AnnouncementsFlagConstants;
import com.liferay.portlet.announcements.service.AnnouncementsEntryServiceUtil;
import com.liferay.portlet.announcements.service.AnnouncementsFlagLocalServiceUtil;
import com.liferay.portlet.asset.NoSuchEntryException;
import com.liferay.portlet.asset.model.AssetEntry;
import com.liferay.portlet.asset.service.AssetEntryLocalServiceUtil;
import com.liferay.portlet.documentlibrary.DuplicateFileException;
import com.liferay.portlet.documentlibrary.model.DLFileEntry;
import com.liferay.portlet.documentlibrary.model.DLFolder;
import com.liferay.portlet.documentlibrary.model.DLFolderConstants;
import com.liferay.portlet.documentlibrary.service.DLAppLocalServiceUtil;
import com.liferay.portlet.documentlibrary.service.DLFileEntryLocalServiceUtil;
import com.liferay.portlet.documentlibrary.service.DLFolderLocalServiceUtil;
import com.liferay.portlet.messageboards.model.MBCategory;
import com.liferay.portlet.messageboards.service.MBCategoryLocalServiceUtil;
import com.liferay.util.CourseCopyUtil;
import com.tls.lms.util.DLFolderUtil;

public class CloneCourse implements MessageListener {
	private static Log log = LogFactoryUtil.getLog(CloneCourse.class);


	long groupId;
	
	String newCourseName;
	
	ThemeDisplay themeDisplay;
	ServiceContext serviceContext;
	
	Date startDate;
	Date endDate;
	
	boolean visible;
	boolean includeTeacher;
	
	boolean cloneForum;
	
	private String cloneTraceStr = "--------------- Clone course trace ----------------"; 
	private Boolean childCourse; 	
	public CloneCourse(long groupId, String newCourseName, ThemeDisplay themeDisplay, Date startDate, Date endDate, boolean cloneForum, ServiceContext serviceContext) {
		super();
		this.groupId = groupId;
		this.newCourseName = newCourseName;
		this.themeDisplay = themeDisplay;
		this.startDate = startDate;
		this.endDate = endDate;
		this.cloneForum = cloneForum;
		this.serviceContext = serviceContext;
	}
	

	public CloneCourse() {
	}
	
	
	
	@Override
	public void receive(Message message) throws MessageListenerException {
		
		try {
			
			this.groupId	= message.getLong("groupId");
			this.newCourseName = message.getString("newCourseName");
			
			this.startDate 	= (Date)message.get("startDate");
			this.endDate 	= (Date)message.get("endDate");
			
			this.serviceContext = (ServiceContext)message.get("serviceContext");
			this.themeDisplay = (ThemeDisplay)message.get("themeDisplay");
			this.childCourse =(Boolean)message.get("childCourse");
			
			this.visible = message.getBoolean("visible");
			this.includeTeacher = message.getBoolean("includeTeacher");
			this.cloneForum = message.getBoolean("cloneForum");
			Role adminRole = RoleLocalServiceUtil.getRole(themeDisplay.getCompanyId(),"Administrator");
			List<User> adminUsers = UserLocalServiceUtil.getRoleUsers(adminRole.getRoleId());
			 
			PrincipalThreadLocal.setName(adminUsers.get(0).getUserId());
			PermissionChecker permissionChecker =PermissionCheckerFactoryUtil.create(adminUsers.get(0), true);
			PermissionThreadLocal.setPermissionChecker(permissionChecker);
		
			doCloneCourse();
			
			log.debug("Clone Stack Trace: "+cloneTraceStr);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unchecked")
	public void doCloneCourse() throws Exception {
		cloneTraceStr += " Course to clone\n........................." + groupId;
		
		log.debug("  + groupId: "+groupId);
		
		Group group = GroupLocalServiceUtil.getGroup(groupId);
		Course course = CourseLocalServiceUtil.getCourseByGroupCreatedId(groupId);
				
		log.debug("  + course: "+course.getTitle(themeDisplay.getLocale()));
		cloneTraceStr += " course:" + course.getTitle(themeDisplay.getLocale()); 
		cloneTraceStr += " groupId:" + groupId;
		
		Date today=new Date(System.currentTimeMillis());

		String courseTemplate = this.serviceContext.getRequest().getParameter("courseTemplate");
		long layoutSetPrototypeId = 0;
		if(courseTemplate.indexOf("&")>-1){
			layoutSetPrototypeId = Long.parseLong(courseTemplate.split("&")[1]);
		}else{
			layoutSetPrototypeId = Long.parseLong(courseTemplate);
		}		
		
		
		log.debug("  + layoutSetPrototypeId: "+layoutSetPrototypeId);
		cloneTraceStr += " layoutSetPrototypeId:" + layoutSetPrototypeId;
		
		try{
			AssetEntryLocalServiceUtil.validate(course.getGroupCreatedId(), Course.class.getName(), serviceContext.getAssetCategoryIds(), serviceContext.getAssetTagNames());
			serviceContext.setAssetCategoryIds(AssetEntryLocalServiceUtil.getEntry(Course.class.getName(), course.getCourseId()).getCategoryIds());
			log.debug("  + AssetCategoryIds: "+AssetEntryLocalServiceUtil.getEntry(Course.class.getName(), course.getCourseId()).getCategoryIds().toString());
		}catch(Exception e){
			serviceContext.setAssetCategoryIds(new long[]{});
			//serviceContext.setAssetTagNames(AssetEntryLocalServiceUtil.getEntry(Course.class.getName(), course.getCourseId()).getTags());
		}
		
		//Course newCourse = CourseLocalServiceUtil.addCourse(newCourseName, course.getDescription(), "", themeDisplay.getLocale() , today, startDate, endDate, serviceContext, course.getCalificationType());
		
		//when lmsprefs has more than one lmstemplate selected the addcourse above throws an error.
		
		
		int typeSite = GroupLocalServiceUtil.getGroup(course.getGroupCreatedId()).getType();
		Course newCourse = null;  
		String summary = new String();
		try{
			summary = AssetEntryLocalServiceUtil.getEntry(Course.class.getName(),course.getCourseId()).getSummary(themeDisplay.getLocale());
			newCourse = CourseLocalServiceUtil.addCourse(newCourseName, course.getDescription(themeDisplay.getLocale()),summary
					, "", themeDisplay.getLocale(), today, startDate, endDate, layoutSetPrototypeId, typeSite, serviceContext, course.getCalificationType(), (int)course.getMaxusers(),true);
			
			newCourse.setWelcome(course.getWelcome());
			newCourse.setWelcomeMsg(course.getWelcomeMsg());
			newCourse.setWelcomeSubject(course.getWelcomeSubject());
			newCourse.setGoodbye(course.getGoodbye());
			newCourse.setGoodbyeMsg(course.getGoodbyeMsg());
			newCourse.setGoodbyeSubject(course.getGoodbyeSubject());
			newCourse.setCourseEvalId(course.getCourseEvalId());
			
		} catch(DuplicateGroupException e){
			if(log.isDebugEnabled())e.printStackTrace();
			throw new DuplicateGroupException();
		}
	
		newCourse.setExpandoBridgeAttributes(serviceContext);
		
		newCourse.getExpandoBridge().setAttributes(course.getExpandoBridge().getAttributes());
		//Course newCourse = CourseLocalServiceUtil.addCourse(newCourseName, course.getDescription(), "", "", themeDisplay.getLocale(), today, today, today, layoutSetPrototypeId, serviceContext);
		if(this.childCourse)
		{
			List<CourseCompetence> courseCompetences= CourseCompetenceLocalServiceUtil.findBycourseId(course.getCourseId(), false);
			for(CourseCompetence courseCompetence:courseCompetences)
			{
				long courseCompetenceId = CounterLocalServiceUtil.increment(CourseCompetence.class.getName());
				CourseCompetence cc = CourseCompetenceLocalServiceUtil.createCourseCompetence(courseCompetenceId);
				cc.setCourseId(newCourse.getCourseId());
				cc.setCompetenceId(courseCompetence.getCompetenceId());
				cc.setCachedModel(courseCompetence.getCondition());
				cc.setCondition(courseCompetence.getCondition());
				CourseCompetenceLocalServiceUtil.updateCourseCompetence(cc, true);
			}
			courseCompetences= CourseCompetenceLocalServiceUtil.findBycourseId(course.getCourseId(), true);
			for(CourseCompetence courseCompetence:courseCompetences)
			{
				long courseCompetenceId = CounterLocalServiceUtil.increment(CourseCompetence.class.getName());
				CourseCompetence cc = CourseCompetenceLocalServiceUtil.createCourseCompetence(courseCompetenceId);
				cc.setCourseId(newCourse.getCourseId());
				cc.setCompetenceId(courseCompetence.getCompetenceId());
				cc.setCachedModel(courseCompetence.getCondition());
				cc.setCondition(courseCompetence.getCondition());
				CourseCompetenceLocalServiceUtil.updateCourseCompetence(cc, true);
			}
		}
		Group newGroup = GroupLocalServiceUtil.getGroup(newCourse.getGroupCreatedId());
		serviceContext.setScopeGroupId(newCourse.getGroupCreatedId());
		
		if(this.childCourse)
		{
			log.debug("hijo de:"+Long.toString(course.getCourseId()));
			newCourse.setParentCourseId(course.getCourseId());
			CourseLocalServiceUtil.setVisible(newCourse.getCourseId(), false);
		}
		
		Role siteMemberRole = RoleLocalServiceUtil.getRole(themeDisplay.getCompanyId(), RoleConstants.SITE_MEMBER);
		
		
		newCourse.setIcon(course.getIcon());
		
		try{
			newCourse = CourseLocalServiceUtil.modCourse(newCourse, serviceContext);
			
			AssetEntry entry = AssetEntryLocalServiceUtil.getEntry(Course.class.getName(),newCourse.getCourseId());
			entry.setVisible(visible);
			entry.setSummary(summary);
			AssetEntryLocalServiceUtil.updateAssetEntry(entry);
			newGroup.setName(newCourse.getTitle(themeDisplay.getLocale(), true));
			newGroup.setDescription(summary);
			GroupLocalServiceUtil.updateGroup(newGroup);
			
		}catch(Exception e){
			if(log.isDebugEnabled())e.printStackTrace();
		}
		
		newCourse.setUserId(themeDisplay.getUserId());

		log.debug("-----------------------\n  From course: "+  group.getName());
		log.debug("  + to course: "+  newCourse.getTitle(Locale.getDefault()) +", GroupCreatedId: "+newCourse.getGroupCreatedId()+", GroupId: "+newCourse.getGroupId());
		cloneTraceStr += "\n New course\n........................." + groupId;
		cloneTraceStr += " Course: "+  newCourse.getTitle(Locale.getDefault()) +"\n GroupCreatedId: "+newCourse.getGroupCreatedId()+"\n GroupId: "+newCourse.getGroupId();
		cloneTraceStr += "\n.........................";
		
		/**
		 * METO AL USUARIO CREADOR DEL CURSO COMO PROFESOR
		 */
		if(includeTeacher){
			log.debug(includeTeacher);
			if (!GroupLocalServiceUtil.hasUserGroup(themeDisplay.getUserId(), newCourse.getGroupCreatedId())) {
					GroupLocalServiceUtil.addUserGroups(themeDisplay.getUserId(),	new long[] { newCourse.getGroupCreatedId() });
				//The application only send one mail at listener
				//User user = UserLocalServiceUtil.getUser(userId);
				//sendEmail(user, course);
				}
			LmsPrefs lmsPrefs=LmsPrefsLocalServiceUtil.getLmsPrefs(themeDisplay.getCompanyId());

			long teacherRoleId=RoleLocalServiceUtil.getRole(lmsPrefs.getEditorRole()).getRoleId();
				UserGroupRoleLocalServiceUtil.addUserGroupRoles(new long[] { themeDisplay.getUserId() }, newCourse.getGroupCreatedId(), teacherRoleId);
				
		}
		
		
		
		/*********************************************************/
		
		
		/*long days = 0;
		boolean isFirstModule = true;*/
		
		LearningActivityTypeRegistry learningActivityTypeRegistry = new LearningActivityTypeRegistry();
		List<Module> modules = ModuleLocalServiceUtil.findAllInGroup(groupId);
		HashMap<Long,Long> correlationActivities = new HashMap<Long, Long>();
		HashMap<Long,Long> correlationModules = new HashMap<Long, Long>();
		HashMap<Long,Long> modulesDependencesList = new  HashMap<Long, Long>();
		for(Module module:modules){
			Module newModule;

			try {
				newModule = ModuleLocalServiceUtil.createModule(CounterLocalServiceUtil.increment(Module.class.getName()));
				correlationModules.put(module.getModuleId(), newModule.getModuleId());
				
				if(module.getPrecedence()!=0){
					modulesDependencesList.put(module.getModuleId(),module.getPrecedence());
				}

				if(this.childCourse)
				{
					newModule.setUuid(module.getUuid());
				}
				
				newModule.setTitle(module.getTitle());
				newModule.setDescription(module.getDescription());
				newModule.setGroupId(newCourse.getGroupId());
				
				newModule.setCompanyId(newCourse.getCompanyId());
				newModule.setGroupId(newCourse.getGroupCreatedId());
				newModule.setUserId(newCourse.getUserId());
				newModule.setOrdern(newModule.getModuleId());
				newModule.setAllowedTime(module.getAllowedTime());
				
				//Icono
				newModule.setIcon(module.getIcon());
				
			
				newModule.setStartDate(startDate);
				newModule.setEndDate(endDate);
				
				newModule.setDescription(descriptionFilesClone(module.getDescription(),newCourse.getGroupCreatedId(), newModule.getModuleId(),themeDisplay.getUserId()));
				
				ModuleLocalServiceUtil.addModule(newModule);
				
				log.debug("\n    Module : " + module.getTitle(Locale.getDefault()) +"("+module.getModuleId()+")");
				log.debug("    + Module : " + newModule.getTitle(Locale.getDefault()) +"("+newModule.getModuleId()+")" );
				cloneTraceStr += "  Module: " + newModule.getTitle(Locale.getDefault()) +"("+newModule.getModuleId()+")";
				
			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}
			
			List<LearningActivity> activities = LearningActivityLocalServiceUtil.getLearningActivitiesOfModule(module.getModuleId());
			HashMap<Long, Long> pending = new HashMap<Long, Long>();
			List<Long> evaluations = new ArrayList<Long>(); 
			for(LearningActivity activity:activities){
				
				LearningActivity newLearnActivity;
				LearningActivity nuevaLarn = null;
				try {
					newLearnActivity = LearningActivityLocalServiceUtil.createLearningActivity(CounterLocalServiceUtil.increment(LearningActivity.class.getName()));
					if(this.childCourse)
					{
						newLearnActivity.setUuid(activity.getUuid());
					}
					newLearnActivity.setTitle(activity.getTitle());
					newLearnActivity.setDescription(activity.getDescription());
					newLearnActivity.setTypeId(activity.getTypeId());
					//Cuando es tipo Evaluación no hay que llevarse el extracontent
					newLearnActivity.setExtracontent(activity.getExtracontent());
					
					
					newLearnActivity.setTries(activity.getTries());
					newLearnActivity.setPasspuntuation(activity.getPasspuntuation());
					newLearnActivity.setPriority(newLearnActivity.getActId());
					
					boolean actPending = false;
					if(activity.getPrecedence()>0){
						if(correlationActivities.get(activity.getPrecedence())==null){
							actPending = true;
						}else{
							newLearnActivity.setPrecedence(correlationActivities.get(activity.getPrecedence()));
						}
					}
					
					newLearnActivity.setWeightinmodule(activity.getWeightinmodule());
					
					newLearnActivity.setGroupId(newModule.getGroupId());
					newLearnActivity.setModuleId(newModule.getModuleId());
					
					newLearnActivity.setStartdate(startDate);
					newLearnActivity.setEnddate(endDate);
					
			

					newLearnActivity.setDescription(descriptionFilesClone(activity.getDescription(),newModule.getGroupId(), newLearnActivity.getActId(),themeDisplay.getUserId()));
		
					nuevaLarn=LearningActivityLocalServiceUtil.addLearningActivity(newLearnActivity,serviceContext);

					log.debug("      Learning Activity : " + activity.getTitle(Locale.getDefault())+ " ("+activity.getActId()+", " + LanguageUtil.get(Locale.getDefault(),learningActivityTypeRegistry.getLearningActivityType(activity.getTypeId()).getName())+")");
					log.debug("      + Learning Activity : " + nuevaLarn.getTitle(Locale.getDefault())+ " ("+nuevaLarn.getActId()+", " + LanguageUtil.get(Locale.getDefault(),learningActivityTypeRegistry.getLearningActivityType(nuevaLarn.getTypeId()).getName())+")");
					cloneTraceStr += "   Learning Activity: " + nuevaLarn.getTitle(Locale.getDefault())+ " ("+nuevaLarn.getActId()+", " + LanguageUtil.get(Locale.getDefault(),learningActivityTypeRegistry.getLearningActivityType(nuevaLarn.getTypeId()).getName())+")";
					
					CourseCopyUtil.cloneActivityFile(activity, nuevaLarn, themeDisplay.getUserId(), serviceContext);
					
					
					long actId = nuevaLarn.getActId();
					correlationActivities.put(activity.getActId(), actId);
					
					boolean visible = ResourcePermissionLocalServiceUtil.hasResourcePermission(siteMemberRole.getCompanyId(), LearningActivity.class.getName(), 
							ResourceConstants.SCOPE_INDIVIDUAL,	Long.toString(actId),siteMemberRole.getRoleId(), ActionKeys.VIEW);
					
					if(!visible) {
						ResourcePermissionLocalServiceUtil.setResourcePermissions(siteMemberRole.getCompanyId(), LearningActivity.class.getName(), 
								ResourceConstants.SCOPE_INDIVIDUAL,	Long.toString(actId),siteMemberRole.getRoleId(), new String[] {ActionKeys.VIEW});
					}
					
					if(nuevaLarn.getTypeId() == 8){
						evaluations.add(nuevaLarn.getActId());
					}
					
					
					if(actPending){
						pending.put(actId, activity.getPrecedence());
					}
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				}

				cloneTraceStr += CourseCopyUtil.createTestQuestionsAndAnswers(activity, nuevaLarn, newModule, themeDisplay.getUserId(), cloneTraceStr);
				
				
			}
			
			if(pending.size()>0){
				for(Long id : pending.keySet()){
					LearningActivity la = LearningActivityLocalServiceUtil.getLearningActivity(id);
					
					if(log.isDebugEnabled())log.debug(la);
					if(la!=null){
						Long idAsig = pending.get(id);

						if(log.isDebugEnabled())log.debug(idAsig);
						if(idAsig!=null){
							Long other = correlationActivities.get(idAsig);
							if(log.isDebugEnabled())log.debug(other);
							la.setPrecedence(other);
							
							LearningActivityLocalServiceUtil.updateLearningActivity(la);
						}
					}
				}
			}
			
			
			//Extra Content de las evaluaciones
			CourseCopyUtil.copyEvaluationExtraContent(evaluations, correlationActivities);
			
		}	
		
		
		//Dependencias de modulos
		log.debug("modulesDependencesList "+modulesDependencesList.keySet());
		for(Long id : modulesDependencesList.keySet()){
			//id del modulo actual
			Long moduleToBePrecededNew = correlationModules.get(id);
			Long modulePredecesorIdOld =  modulesDependencesList.get(id);
			Long modulePredecesorIdNew = correlationModules.get(modulePredecesorIdOld);
			Module moduleNew = ModuleLocalServiceUtil.getModule(moduleToBePrecededNew);
			moduleNew.setPrecedence(modulePredecesorIdNew);
			ModuleLocalServiceUtil.updateModule(moduleNew);
		}
		
		if(this.cloneForum){
		
			//-------------------------------------------
			//Categorias y subcategorias del foro
			//-------------------------------------
			List<MBCategory> listCategories = MBCategoryLocalServiceUtil.getCategories(groupId);
			
			//Si existen las categorias se clonan
			if(listCategories!=null && listCategories.size()>0){
				
				log.debug("------------------------Foro:: listCategories.size:: " + listCategories.size());
				
				long newCourseGroupId = newCourse.getGroupCreatedId();//Para asociar las categorias creadas con el nuevo curso
				
				boolean resultCloneForo = cloneForo(newCourseGroupId, listCategories);
				
				log.debug("----------------------- Foro: CloneCat:: " + resultCloneForo);

			}
			
			
			//---------------------------------------------------------------------
			
		}
		
		log.debug(" ENDS!");
		
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss");
		dateFormat.setTimeZone(themeDisplay.getTimeZone());
		
		String[] args = {newCourse.getTitle(themeDisplay.getLocale()), dateFormat.format(startDate), dateFormat.format(endDate)};
		
		sendNotification(LanguageUtil.get(themeDisplay.getLocale(),"courseadmin.clone.confirmation.title"), LanguageUtil.format(themeDisplay.getLocale(),"courseadmin.clone.confirmation.message", args), themeDisplay.getPortalURL()+"/web/"+newGroup.getFriendlyURL(), "Avisos", 1);
	
	}
	
	public String descriptionFilesClone(String description, long groupId, long actId, long userId){

		String newDescription = description;
		
		try {
			
			Document document = SAXReaderUtil.read(description.replace("&lt;","<").replace("&nbsp;",""));
			
			Element rootElement = document.getRootElement();
			
			if(rootElement.elements("Description").size()!=0){
				for (Element entryElement : rootElement.elements("Description")) {
					for (Element entryElementP : entryElement.elements("p")) {
						
						//Para las imagenes
						for (Element entryElementImg : entryElementP.elements("img")) {
							
							String src = entryElementImg.attributeValue("src");
							
							String []srcInfo = src.split("/");
							String fileUuid = "", fileGroupId ="";
							
							if(srcInfo.length >= 6  && srcInfo[1].compareTo("documents") == 0){
								fileUuid = srcInfo[srcInfo.length-1];
								fileGroupId = srcInfo[2];
								
								String []uuidInfo = fileUuid.split("\\?");
								String uuid="";
								if(srcInfo.length > 0){
									uuid=uuidInfo[0];
								}
								
								FileEntry file;
								try {
									file = DLAppLocalServiceUtil.getFileEntryByUuidAndGroupId(uuid, Long.parseLong(fileGroupId));
									
									ServiceContext serviceContext = new ServiceContext();
									serviceContext.setScopeGroupId(groupId);
									serviceContext.setUserId(userId);
									serviceContext.setCompanyId(file.getCompanyId());
									serviceContext.setAddGroupPermissions(true);
									
									FileEntry newFile = CourseCopyUtil.cloneFileDescription(file, actId, file.getUserId(), serviceContext);
									
									newDescription = descriptionCloneFile(newDescription, file, newFile);
									
									log.debug("     + Description file image : " + file.getTitle() +" ("+file.getMimeType()+")");
									
								} catch (Exception e) {
									// TODO Auto-generated catch block
									//e.printStackTrace();
									log.error("* ERROR! Description file image : " + e.getMessage());
								}
							}
						}
						
						//Para los enlaces
						for (Element entryElementLink : entryElementP.elements("a")) {
							
							String href = entryElementLink.attributeValue("href");
							
							String []hrefInfo = href.split("/");
							String fileUuid = "", fileGroupId ="";
							
							if(hrefInfo.length >= 6 && hrefInfo[1].compareTo("documents") == 0){
								fileUuid = hrefInfo[hrefInfo.length-1];
								fileGroupId = hrefInfo[2];
								
								String []uuidInfo = fileUuid.split("\\?");
								String uuid="";
								if(hrefInfo.length > 0){
									uuid=uuidInfo[0];
								}
								
								FileEntry file;
								try {
									file = DLAppLocalServiceUtil.getFileEntryByUuidAndGroupId(uuid, Long.parseLong(fileGroupId));
																			
									ServiceContext serviceContext = new ServiceContext();
									serviceContext.setScopeGroupId(groupId);
									serviceContext.setUserId(userId);
									serviceContext.setCompanyId(file.getCompanyId());
									serviceContext.setAddGroupPermissions(true);
									
									FileEntry newFile = CourseCopyUtil.cloneFileDescription(file, actId, file.getUserId(), serviceContext);
									
									newDescription = descriptionCloneFile(newDescription, file, newFile);
									
									log.debug("   + Description file pdf : " + file.getTitle() +" "+file.getFileEntryId() );
									
								} catch (Exception e) {
									// TODO Auto-generated catch block
									//e.printStackTrace();
									log.error("* ERROR! Description file pdf : " + e.getMessage());
								}
							}
							
							//Si en los enlaces tienen una imagen para hacer click.
							/*for (Element entryElementLinkImage : entryElementLink.elements("img")) {
								;//parseImage(entryElementLinkImage, element, context, moduleId);
							}*/
							
						}
					}
				}
			}else{
				if (rootElement.getQName().getName().equals("p")) {
					
					//Para las imagenes
					for (Element entryElementImg : rootElement.elements("img")) {
						
						String src = entryElementImg.attributeValue("src");
						
						String []srcInfo = src.split("/");
						String fileUuid = "", fileGroupId ="";
						
						if(srcInfo.length >= 6  && srcInfo[1].compareTo("documents") == 0){
							fileUuid = srcInfo[srcInfo.length-1];
							fileGroupId = srcInfo[2];
							
							String []uuidInfo = fileUuid.split("\\?");
							String uuid="";
							if(srcInfo.length > 0){
								uuid=uuidInfo[0];
							}
							
							FileEntry file;
							try {
								file = DLAppLocalServiceUtil.getFileEntryByUuidAndGroupId(uuid, Long.parseLong(fileGroupId));
								
								ServiceContext serviceContext = new ServiceContext();
								serviceContext.setScopeGroupId(groupId);
								serviceContext.setUserId(userId);
								serviceContext.setCompanyId(file.getCompanyId());
								serviceContext.setAddGroupPermissions(true);
								
								FileEntry newFile = CourseCopyUtil.cloneFileDescription(file, actId, file.getUserId(), serviceContext);
								
								newDescription = descriptionCloneFile(newDescription, file, newFile);
								
								log.debug("     + Description file image : " + file.getTitle() +" ("+file.getMimeType()+")");
								
							} catch (Exception e) {
								// TODO Auto-generated catch block
								//e.printStackTrace();
								log.error("* ERROR! Description file image : " + e.getMessage());
							}
						}
					}
					
					//Para los enlaces
					for (Element entryElementLink : rootElement.elements("a")) {
						
						String href = entryElementLink.attributeValue("href");
						
						String []hrefInfo = href.split("/");
						String fileUuid = "", fileGroupId ="";
						
						if(hrefInfo.length >= 6 && hrefInfo[1].compareTo("documents") == 0){
							fileUuid = hrefInfo[hrefInfo.length-1];
							fileGroupId = hrefInfo[2];
							
							String []uuidInfo = fileUuid.split("\\?");
							String uuid="";
							if(hrefInfo.length > 0){
								uuid=uuidInfo[0];
							}
							
							FileEntry file;
							try {
								file = DLAppLocalServiceUtil.getFileEntryByUuidAndGroupId(uuid, Long.parseLong(fileGroupId));
																		
								ServiceContext serviceContext = new ServiceContext();
								serviceContext.setScopeGroupId(groupId);
								serviceContext.setUserId(userId);
								serviceContext.setCompanyId(file.getCompanyId());
								serviceContext.setAddGroupPermissions(true);
								
								FileEntry newFile = CourseCopyUtil.cloneFileDescription(file, actId, file.getUserId(), serviceContext);
								
								newDescription = descriptionCloneFile(newDescription, file, newFile);
								
								log.debug("   + Description file pdf : " + file.getTitle() +" "+file.getFileEntryId() );
								
							} catch (Exception e) {
								// TODO Auto-generated catch block
								//e.printStackTrace();
								log.error("* ERROR! Description file pdf : " + e.getMessage());
							}
						}
						
						//Si en los enlaces tienen una imagen para hacer click.
						for (Element entryElementLinkImage : entryElementLink.elements("img")) {
							;//parseImage(entryElementLinkImage, element, context, moduleId);
						}
						
					}
				}
			}
			
			
			
		} catch (DocumentException de) {
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			log.error("* ERROR! Document Exception : " + e.getMessage());
		}

		return newDescription;
		
	}
	
	private String descriptionCloneFile(String description, FileEntry oldFile, FileEntry newFile){
		String res = description;
		
		//Precondicion
		if(oldFile == null || newFile == null){
			return res;
		}
		
		//<img src="/documents/10808/0/GibbonIndexer.jpg/b24c4a8f-e65c-434a-ba36-3b3e10b21a8d?t=1376472516221"
		//<a  href="/documents/10808/10884/documento.pdf/32c193ed-16b3-4a83-93da-630501b72ee4">Documento</a></p>
		
		String target 		= "/documents/"+oldFile.getRepositoryId()+"/"+oldFile.getFolderId()+"/"+URLEncoder.encode(oldFile.getTitle())+"/"+oldFile.getUuid();
		String replacement 	= "/documents/"+newFile.getRepositoryId()+"/"+newFile.getFolderId()+"/"+URLEncoder.encode(newFile.getTitle())+"/"+newFile.getUuid();

		res = description.replace(target, replacement);
		
		if(log.isDebugEnabled()){
			if(res.equals(description)){
				log.debug("   :: description         : " + description );
				log.debug("   :: target      : " + target );	
				log.debug("   :: replacement : " + replacement );
			}
		}
		
				
		String changed = (!res.equals(description))?" changed":" not changed";
		
		log.debug("   + Description file : " + newFile.getTitle() +" (" + newFile.getMimeType() + ")" + changed);
		
		return res;
	}
	
	
	
	private void sendNotification(String title, String content, String url, String type, int priority){
		
		//ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);	
		SimpleDateFormat formatDay = new SimpleDateFormat("dd");
		formatDay.setTimeZone(themeDisplay.getTimeZone());
		SimpleDateFormat formatMonth = new SimpleDateFormat("MM");
		formatMonth.setTimeZone(themeDisplay.getTimeZone());
		SimpleDateFormat formatYear = new SimpleDateFormat("yyyy");
		formatYear.setTimeZone(themeDisplay.getTimeZone());
		SimpleDateFormat formatHour = new SimpleDateFormat("HH");
		formatHour.setTimeZone(themeDisplay.getTimeZone());
		SimpleDateFormat formatMin = new SimpleDateFormat("mm");
		formatMin.setTimeZone(themeDisplay.getTimeZone());
		
		Date today=new Date(System.currentTimeMillis());
		
		int displayDateDay=Integer.parseInt(formatDay.format(today));
		int displayDateMonth=Integer.parseInt(formatMonth.format(today))-1;
		int displayDateYear=Integer.parseInt(formatYear.format(today));
		int displayDateHour=Integer.parseInt(formatHour.format(today));
		int displayDateMinute=Integer.parseInt(formatMin.format(today));
		
		int expirationDateDay=Integer.parseInt(formatDay.format(today));
		int expirationDateMonth=Integer.parseInt(formatMonth.format(today))-1;
		int expirationDateYear=Integer.parseInt(formatYear.format(today))+1;
		int expirationDateHour=Integer.parseInt(formatHour.format(today));
		int expirationDateMinute=Integer.parseInt(formatMin.format(today));

		long classNameId=PortalUtil.getClassNameId(User.class.getName());
		long classPK=themeDisplay.getUserId();

		AnnouncementsEntry ae;
		try {
			ae = AnnouncementsEntryServiceUtil.addEntry(
			                            themeDisplay.getPlid(), classNameId, classPK, title, content, url, type, 
			                            displayDateMonth, displayDateDay, displayDateYear, displayDateHour, displayDateMinute,
			                            expirationDateMonth, expirationDateDay, expirationDateYear, expirationDateHour, expirationDateMinute,
			                            priority, false);
			
			AnnouncementsFlagLocalServiceUtil.addFlag(classPK,ae.getEntryId(),AnnouncementsFlagConstants.UNREAD);
		} catch (PortalException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SystemException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		                            
	}
	
	/**
	 * Clona las categorias/subcategorias/subsubcategorias... del foro
	 * @param newCourseGroupId: groupId del curso clonado
	 * @param listCategories: lista de categorias del foro del curso
	 * 
	 * @return: boolean = true -> Se realiza correctamente
	 * 			boolean = false -> Se ha producido algun error durante el proceso
	 */
	private boolean cloneForo(long newCourseGroupId, List<MBCategory> listCategories){
		
		boolean resultCloneForo = true;
		
		List<MBCategory> listParentCat = new ArrayList<MBCategory>();
		List<MBCategory> listSubCat = new ArrayList<MBCategory>();
		
		long parentCategoryId = 0;
		
		for(MBCategory category:listCategories){
			if(0 == category.getParentCategoryId()) listParentCat.add(category);
			else listSubCat.add(category);
		}
		
		resultCloneForo = subCategories(parentCategoryId, newCourseGroupId, listParentCat, listSubCat);
		
		return resultCloneForo;
	}
	
	/**
	 * 
	 * Funcion recursiva que clona las categorias del foro y busca si cada categoria tiene una subcategoria para
	 * volver a realizar la misma operacion
	 * 
	 * @param parentCategoryId: Id de la categoria padre (la clonada)
	 * @param newCourseGroupId: Id del curso clonado
	 * @param listParentCat
	 * @param listSubCat
	 * @return: boolean = true -> Si se realiza el proceso correctamente
	 * 			boolean = false -> Si se produce algun error durante el proceso
	 */
	private boolean subCategories(long parentCategoryId, long newCourseGroupId, List<MBCategory> listParentCat, List<MBCategory> listSubCat){
		
		log.debug("-----------Clone Foro: listParentCat.size:: [" + listParentCat.size() + "], listSubCat.size :: [" + listSubCat.size() + "]");
		
		boolean result = true;
		
		long newParentCategoryId;
		
		MBCategory newCourseCategory = null;
		
		List<MBCategory> listSubSubCat;
		List<MBCategory> listParentSubCat;
		
		for(MBCategory category:listParentCat){
			
			newCourseCategory = createNewCategory(newCourseGroupId, category, parentCategoryId);
			if (newCourseCategory==null) return false;
			
			newParentCategoryId = newCourseCategory.getCategoryId();
			
			if(listSubCat.size()>0) {
				
				listParentSubCat = new ArrayList<MBCategory>();
				listSubSubCat = new ArrayList<MBCategory>();
				
				for(MBCategory subCategory:listSubCat) {
					
					if(category.getCategoryId() == subCategory.getParentCategoryId()) listParentSubCat.add(subCategory);
					else listSubSubCat.add(subCategory);
		
				}
				
				if(listParentSubCat.size()>0){//Si encuentro subcategorias de esta categoria vuelvo a llamar a esta misma funcion
					result = subCategories(newParentCategoryId, newCourseGroupId, listParentSubCat, listSubSubCat);
					if(!result) return result;
				}
			}
		}
		
		return result;
		
	}
	
	/**
	 * 
	 * Crea una categoria del foro
	 * 
	 * @param newCourseGroupId: Id del curso clonado
	 * @param category: Datos de la categoria que se quiere clonar
	 * @param parentCategoryId: Id de la categoria de la que depende esta categoria (parentCategoryId=0 si no depende de ninguna categoria, y si tiene 
	 * 			dependencia, parentCategoryId = categoryId de la categoria de la que depende)
	 * @return 	null -> en caso de que se produzca algun error
	 * 			Objeto MBCategory creado en caso de que la operacion se realice correctamente
	 */
	private MBCategory createNewCategory(long newCourseGroupId, MBCategory category, long parentCategoryId) {
		
		log.debug("-----------Clone Foro: createNewCategory:: newCourseGroupId:: [" + newCourseGroupId + "], category:: [" + category.getName() + "], parentCatId:: [" + parentCategoryId + "]");
		MBCategory newCourseCategory = null;
		
		try {
			
			newCourseCategory = MBCategoryLocalServiceUtil.createMBCategory(CounterLocalServiceUtil.increment(MBCategory.class.getName()));
			newCourseCategory.setGroupId(newCourseGroupId);
			newCourseCategory.setCompanyId(category.getCompanyId());
			newCourseCategory.setName(category.getName());
			newCourseCategory.setDescription(category.getDescription());
			newCourseCategory.setCreateDate(Calendar.getInstance().getTime());
			newCourseCategory.setModifiedDate(Calendar.getInstance().getTime());
			newCourseCategory.setDisplayStyle(category.getDisplayStyle());
			newCourseCategory.setUserId(themeDisplay.getUserId());
			newCourseCategory.setUserName(themeDisplay.getUser().getFullName());
			
			newCourseCategory.setParentCategoryId(parentCategoryId);
		
			newCourseCategory = MBCategoryLocalServiceUtil.addMBCategory(newCourseCategory);
			
			// Copiar permisos de la categoria antigua en la nueva
    		int [] scopeIds = ResourceConstants.SCOPES;
    		for(int scope : scopeIds) {
    			List<ResourcePermission> resourcePermissionList = ResourcePermissionLocalServiceUtil.getResourcePermissions(category.getCompanyId(), MBCategory.class.getName(), scope, String.valueOf(category.getPrimaryKey()));
        		for(ResourcePermission resourcePermission : resourcePermissionList) {
        			long resourcePermissionId = CounterLocalServiceUtil.increment(ResourcePermission.class.getName());
        			ResourcePermission rpNew = ResourcePermissionLocalServiceUtil.createResourcePermission(resourcePermissionId);
        			rpNew.setActionIds(resourcePermission.getActionIds());
        			rpNew.setCompanyId(resourcePermission.getCompanyId());
        			rpNew.setName(resourcePermission.getName());
        			rpNew.setRoleId(resourcePermission.getRoleId());
        			rpNew.setScope(resourcePermission.getScope());
        			rpNew.setPrimKey(String.valueOf(newCourseCategory.getCategoryId()));
        			rpNew.setOwnerId(resourcePermission.getOwnerId());
        			rpNew = ResourcePermissionLocalServiceUtil.updateResourcePermission(rpNew);
        		}
    		}
			
			return newCourseCategory;
			
		} catch (SystemException e) {
			log.error(e.getMessage());
			return null;
		}
	}

}
