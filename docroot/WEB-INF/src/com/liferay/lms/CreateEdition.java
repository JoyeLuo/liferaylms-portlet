package com.liferay.lms;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import com.liferay.counter.service.CounterLocalServiceUtil;
import com.liferay.lms.learningactivity.LearningActivityTypeRegistry;
import com.liferay.lms.model.Course;
import com.liferay.lms.model.LearningActivity;
import com.liferay.lms.model.Module;
import com.liferay.lms.service.CourseLocalServiceUtil;
import com.liferay.lms.service.LearningActivityLocalServiceUtil;
import com.liferay.lms.service.ModuleLocalServiceUtil;
import com.liferay.portal.DuplicateGroupException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageListener;
import com.liferay.portal.kernel.messaging.MessageListenerException;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.ResourceConstants;
import com.liferay.portal.model.Role;
import com.liferay.portal.model.RoleConstants;
import com.liferay.portal.model.User;
import com.liferay.portal.security.auth.PrincipalThreadLocal;
import com.liferay.portal.security.permission.ActionKeys;
import com.liferay.portal.security.permission.PermissionChecker;
import com.liferay.portal.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.security.permission.PermissionThreadLocal;
import com.liferay.portal.service.GroupLocalServiceUtil;
import com.liferay.portal.service.ResourcePermissionLocalServiceUtil;
import com.liferay.portal.service.RoleLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.theme.ThemeDisplay;
import com.liferay.portlet.asset.model.AssetEntry;
import com.liferay.portlet.asset.service.AssetEntryLocalServiceUtil;
import com.liferay.util.CourseCopyUtil;

public class CreateEdition implements MessageListener {
	private static Log log = LogFactoryUtil.getLog(CreateEdition.class);

	public static String DOCUMENTLIBRARY_MAINFOLDER = "ResourceUploads";
	private String newEditionName;
	private ThemeDisplay themeDisplay;
	private ServiceContext serviceContext;
	private Date startDate;
	private Date endDate;
	private boolean isLinked;
	private long parentCourseId;
	
	private String cloneTraceStr = "--------------- Creating edition trace ----------------"; 
	public CreateEdition(long groupId, String newEditionName, ThemeDisplay themeDisplay, Date startDate, Date endDate, long parentCourseId, ServiceContext serviceContext) {
		super();
		this.newEditionName = newEditionName;
		this.parentCourseId = parentCourseId;
		this.themeDisplay = themeDisplay;
		this.startDate = startDate;
		this.endDate = endDate;
		this.serviceContext = serviceContext;
	}
	

	public CreateEdition() {
	}
	
	
	
	@Override
	public void receive(Message message) throws MessageListenerException {
		
		try {
			
			this.newEditionName = message.getString("newEditionName");
			this.startDate 	= (Date)message.get("startDate");
			this.endDate 	= (Date)message.get("endDate");
			this.serviceContext = (ServiceContext)message.get("serviceContext");
			this.themeDisplay = (ThemeDisplay)message.get("themeDisplay");
			this.parentCourseId = (Long)message.get("parentCourseId");
			this.isLinked = (Boolean)message.get("isLinked");
			
			log.debug("Parent Course Id: "+parentCourseId);
			Role adminRole = RoleLocalServiceUtil.getRole(themeDisplay.getCompanyId(),"Administrator");
			List<User> adminUsers = UserLocalServiceUtil.getRoleUsers(adminRole.getRoleId());
			 
			PrincipalThreadLocal.setName(adminUsers.get(0).getUserId());
			PermissionChecker permissionChecker =PermissionCheckerFactoryUtil.create(adminUsers.get(0));
			PermissionThreadLocal.setPermissionChecker(permissionChecker);
		
			doCreateEdition();
			
			log.debug("Clone Stack Trace: "+cloneTraceStr);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void doCreateEdition() throws Exception {
		cloneTraceStr += " Course to create edition\n........................." + parentCourseId;
		cloneTraceStr += " New edition name\n........................." + newEditionName;
		Course course = CourseLocalServiceUtil.fetchCourse(parentCourseId);
	
		Group group = GroupLocalServiceUtil.getGroup(course.getGroupCreatedId());
		log.debug("  + Parent course: "+course.getTitle(themeDisplay.getLocale()));
		log.debug(" New edition name\n........................." + newEditionName);
		cloneTraceStr += " Parent course:" + course.getTitle(themeDisplay.getLocale()); 
		
		Date today=new Date(System.currentTimeMillis());

		
		//Plantilla
		long layoutSetPrototypeId = group.getPublicLayoutSet().getLayoutSetPrototypeId();
		log.debug("  + layoutSetPrototypeId: "+layoutSetPrototypeId);
		cloneTraceStr += " layoutSetPrototypeId:" + layoutSetPrototypeId;
	
		
		//Tags y categorias
		try{
			serviceContext.setAssetCategoryIds(AssetEntryLocalServiceUtil.getEntry(Course.class.getName(), course.getCourseId()).getCategoryIds());
			log.debug("  + AssetCategoryIds: "+AssetEntryLocalServiceUtil.getEntry(Course.class.getName(), course.getCourseId()).getCategoryIds().toString());
		}catch(Exception e){
			serviceContext.setAssetCategoryIds(new long[]{});
			
		}
		//Tipo del grupo
		int typeSite = group.getType();
		
		
		//Creamos el nuevo curso para la edición 
		Course newCourse = null;  
		String summary = "";
		
		
		try{
			summary = AssetEntryLocalServiceUtil.getEntry(Course.class.getName(),course.getCourseId()).getSummary(themeDisplay.getLocale());
			newCourse = CourseLocalServiceUtil.addCourse(course.getTitle(themeDisplay.getLocale())+"-"+newEditionName, course.getDescription(themeDisplay.getLocale()),summary
					, "", themeDisplay.getLocale(), today, startDate, endDate, layoutSetPrototypeId, typeSite, serviceContext, course.getCalificationType(), (int)course.getMaxusers(),true);
			
			newCourse.setTitle(newEditionName, themeDisplay.getLocale());
			newCourse.setWelcome(course.getWelcome());
			newCourse.setWelcomeMsg(course.getWelcomeMsg());
			newCourse.setWelcomeSubject(course.getWelcomeSubject());
			newCourse.setGoodbye(course.getGoodbye());
			newCourse.setGoodbyeMsg(course.getGoodbyeMsg());
			newCourse.setGoodbyeSubject(course.getGoodbyeSubject());
			newCourse.setCourseEvalId(course.getCourseEvalId());
			newCourse.setIsLinked(isLinked);
			
		} catch(DuplicateGroupException e){
			if(log.isDebugEnabled())e.printStackTrace();
			throw new DuplicateGroupException();
		}
		newCourse.setExpandoBridgeAttributes(serviceContext);
		newCourse.getExpandoBridge().setAttributes(course.getExpandoBridge().getAttributes());
		newCourse.setParentCourseId(parentCourseId);
		
	
		Group newGroup = GroupLocalServiceUtil.getGroup(newCourse.getGroupCreatedId());
		serviceContext.setScopeGroupId(newCourse.getGroupCreatedId());

		
		Role siteMemberRole = RoleLocalServiceUtil.getRole(themeDisplay.getCompanyId(), RoleConstants.SITE_MEMBER);
		newCourse.setIcon(course.getIcon());
		
		try{
			newCourse = CourseLocalServiceUtil.modCourse(newCourse, serviceContext);
			AssetEntry entry = AssetEntryLocalServiceUtil.getEntry(Course.class.getName(),newCourse.getCourseId());
			entry.setVisible(false);
			entry.setSummary(summary);
			AssetEntryLocalServiceUtil.updateAssetEntry(entry);
			newGroup.setName(course.getTitle(themeDisplay.getLocale(),true)+"-"+newEditionName);
			newGroup.setDescription(summary);
			GroupLocalServiceUtil.updateGroup(newGroup);
			
		}catch(Exception e){
			if(log.isDebugEnabled())e.printStackTrace();
		}
		newCourse.setUserId(themeDisplay.getUserId());

		log.debug("-----------------------\n  Creating edition from: "+  group.getName());
		log.debug("  + editionName : "+  newCourse.getTitle(Locale.getDefault()) +", GroupCreatedId: "+newCourse.getGroupCreatedId()+", GroupId: "+newCourse.getGroupId());
		cloneTraceStr += "\n New edition\n.........................";
		cloneTraceStr += " Edition: "+  newCourse.getTitle(Locale.getDefault()) +"\n GroupCreatedId: "+newCourse.getGroupCreatedId()+"\n GroupId: "+newCourse.getGroupId();
		cloneTraceStr += "\n.........................";
		
		/*********************************************************/
		
		//Create modules and activities
		createModulesAndActivities(newCourse, siteMemberRole, group.getGroupId());
		log.debug(" ENDS!");
	}
	
	
	private void createModulesAndActivities(Course newCourse, Role siteMemberRole, long groupId) throws SystemException{
		
		LearningActivityTypeRegistry learningActivityTypeRegistry = new LearningActivityTypeRegistry();
		List<Module> modules;
		modules = ModuleLocalServiceUtil.findAllInGroup(groupId);
		
		HashMap<Long,Long> correlationModules = new HashMap<Long, Long>();
		HashMap<Long,Long> modulesDependencesList = new  HashMap<Long, Long>();
		Module newModule;
		for(Module module:modules){
			
			try {
				newModule = ModuleLocalServiceUtil.createModule(CounterLocalServiceUtil.increment(Module.class.getName()));
				correlationModules.put(module.getModuleId(), newModule.getModuleId());
				if(module.getPrecedence()!=0){
					modulesDependencesList.put(module.getModuleId(),module.getPrecedence());
				}
				newModule.setUuid(module.getUuid());
				newModule.setTitle(module.getTitle());
				newModule.setDescription(module.getDescription());
				newModule.setGroupId(newCourse.getGroupId());
				newModule.setCompanyId(newCourse.getCompanyId());
				newModule.setGroupId(newCourse.getGroupCreatedId());
				newModule.setUserId(newCourse.getUserId());
				newModule.setOrdern(newModule.getModuleId());
				newModule.setAllowedTime(module.getAllowedTime());
				newModule.setIcon(module.getIcon());
				newModule.setStartDate(startDate);
				newModule.setEndDate(endDate);
				newModule.setDescription(CourseCopyUtil.descriptionFilesClone(module.getDescription(),newCourse.getGroupCreatedId(), newModule.getModuleId(),themeDisplay.getUserId()));
				ModuleLocalServiceUtil.addModule(newModule);
				
				log.debug("\n    Module : " + module.getTitle(Locale.getDefault()) +"("+module.getModuleId()+")");
				log.debug("    + Module : " + newModule.getTitle(Locale.getDefault()) +"("+newModule.getModuleId()+")" );
				cloneTraceStr += "  Module: " + newModule.getTitle(Locale.getDefault()) +"("+newModule.getModuleId()+")";
				createLearningActivities(module, newModule, siteMemberRole,learningActivityTypeRegistry);	
				
			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}
			
		}	
		
		
		//Dependencias de modulos
		log.debug("modulesDependencesList "+modulesDependencesList.keySet());
		Long moduleToBePrecededNew;
		Long modulePredecesorIdOld;
		Long modulePredecesorIdNew;
		for(Long id : modulesDependencesList.keySet()){
			//id del modulo actual
			moduleToBePrecededNew = correlationModules.get(id);
			modulePredecesorIdOld =  modulesDependencesList.get(id);
			modulePredecesorIdNew = correlationModules.get(modulePredecesorIdOld);
			Module moduleNew = ModuleLocalServiceUtil.fetchModule(moduleToBePrecededNew);
			if(moduleNew!=null){
				moduleNew.setPrecedence(modulePredecesorIdNew);
				ModuleLocalServiceUtil.updateModule(moduleNew);	
			}
		}
	}
	
	
	private void createLearningActivities(Module parentModule, Module newModule, Role siteMemberRole, LearningActivityTypeRegistry learningActivityTypeRegistry) throws SystemException, PortalException{
		HashMap<Long, Long> pending = new HashMap<Long, Long>();
		HashMap<Long,Long> correlationActivities = new HashMap<Long, Long>();
		List<LearningActivity> activities = LearningActivityLocalServiceUtil.getLearningActivitiesOfModule(parentModule.getModuleId());
		LearningActivity newLearnActivity=null;
		LearningActivity nuevaLarn = null;
		boolean canBeLinked = false;
		List<Long> evaluations = new ArrayList<Long>(); 
		for(LearningActivity activity:activities){
			try {
				
				canBeLinked = learningActivityTypeRegistry.getLearningActivityType(activity.getTypeId()).canBeLinked();
				//Fill common columns
				newLearnActivity = LearningActivityLocalServiceUtil.createLearningActivity(CounterLocalServiceUtil.increment(LearningActivity.class.getName()));
				newLearnActivity.setUuid(activity.getUuid());
				newLearnActivity.setTypeId(activity.getTypeId());
				newLearnActivity.setPriority(newLearnActivity.getActId());
				newLearnActivity.setWeightinmodule(activity.getWeightinmodule());
				newLearnActivity.setGroupId(newModule.getGroupId());
				newLearnActivity.setModuleId(newModule.getModuleId());
				newLearnActivity.setStartdate(startDate);
				newLearnActivity.setEnddate(endDate);
				boolean actPending = false;
				if(activity.getPrecedence()>0){
					if(correlationActivities.get(activity.getPrecedence())==null){
						actPending = true;
					}else{
						newLearnActivity.setPrecedence(correlationActivities.get(activity.getPrecedence()));
					}
				}
				
				if(canBeLinked){
					newLearnActivity.setLinkedActivityId(activity.getActId());
				}
				//TODO Cuando esté preparado la parte de linkar no habrá que copiar todo
				//else{
					newLearnActivity.setExtracontent(activity.getExtracontent());
					newLearnActivity.setTitle(activity.getTitle());
					newLearnActivity.setDescription(activity.getDescription());
					newLearnActivity.setTries(activity.getTries());
					newLearnActivity.setPasspuntuation(activity.getPasspuntuation());
					newLearnActivity.setDescription(CourseCopyUtil.descriptionFilesClone(activity.getDescription(),newModule.getGroupId(), newLearnActivity.getActId(),themeDisplay.getUserId()));
				//}
				
				
				nuevaLarn=LearningActivityLocalServiceUtil.addLearningActivity(newLearnActivity,serviceContext);

				log.error("ACTIVITY EXTRA CONTENT BEFORE "+ newLearnActivity.getExtracontent());
				
				log.debug("Learning Activity : " + activity.getTitle(Locale.getDefault())+ " ("+activity.getActId()+", " + LanguageUtil.get(Locale.getDefault(),learningActivityTypeRegistry.getLearningActivityType(activity.getTypeId()).getName())+")");
				log.debug("+Learning Activity : " + nuevaLarn.getTitle(Locale.getDefault())+ " ("+nuevaLarn.getActId()+", " + LanguageUtil.get(Locale.getDefault(),learningActivityTypeRegistry.getLearningActivityType(nuevaLarn.getTypeId()).getName())+") Can Be Linked: "+canBeLinked);
				cloneTraceStr += "   Learning Activity: " + nuevaLarn.getTitle(Locale.getDefault())+ " ("+nuevaLarn.getActId()+", " + LanguageUtil.get(Locale.getDefault(),learningActivityTypeRegistry.getLearningActivityType(nuevaLarn.getTypeId()).getName())+") Can Be Linked: "+canBeLinked;
				

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

			
			//TODO Descomentar cuando esté implementado las actividades linkadas.
			//if(!canBeLinked){
			cloneTraceStr += CourseCopyUtil.createTestQuestionsAndAnswers(activity, nuevaLarn, newModule, themeDisplay.getUserId(), cloneTraceStr);
		
		
		
		}
		
		
		
		//Set the precedences
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
}
