package com.liferay.lms.upgrade;


import java.util.ArrayList;
import java.util.List;

import com.liferay.counter.service.CounterLocalServiceUtil;
import com.liferay.lms.model.Module;
import com.liferay.lms.service.ModuleLocalServiceUtil;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.upgrade.UpgradeProcess;
import com.liferay.portal.model.Company;
import com.liferay.portal.model.ResourceAction;
import com.liferay.portal.model.ResourceConstants;
import com.liferay.portal.model.Role;
import com.liferay.portal.model.RoleConstants;
import com.liferay.portal.service.CompanyLocalServiceUtil;
import com.liferay.portal.service.ResourceActionLocalServiceUtil;
import com.liferay.portal.service.ResourcePermissionLocalServiceUtil;
import com.liferay.portal.service.RoleLocalServiceUtil;

public class UpgradeTo3_1_1 extends UpgradeProcess {
	public int getThreshold() {
		return 211;
	}

	protected void doUpgrade() throws Exception {
		// your upgrade code here.
		
		 Role siteMember;
	    
		System.out.println("--- UPGRADING LMS TO 3.1.1 ");
		System.out.println("--- CREATING ACCESS PERMISSION TO MODULE ");
		
		List<String> actionIds = new ArrayList<String>();
		actionIds.add("ACCESS");
		ResourceActionLocalServiceUtil.checkResourceActions(
				Module.class.getName(), actionIds);
		
		System.out.println("--- SETTING ACCESS PERMISSION TO COURSE ADMINISTRATION");
	    List<Company> companys = CompanyLocalServiceUtil.getCompanies();
	    for(Company company : companys){
	    	try{
	    		 Role courseAdministrator = RoleLocalServiceUtil.getRole(company.getCompanyId(), "Administrador de cursos");
		    	 if(courseAdministrator!=null){
		    		  ResourcePermissionLocalServiceUtil.setResourcePermissions(company.getCompanyId(), 
				     			Module.class.getName(),ResourceConstants.SCOPE_COMPANY, String.valueOf(company.getCompanyId()),  courseAdministrator.getRoleId(),  new String[]{"ACCESS","VIEW","DELETE", "ADD_LACT","UPDATE","PERMISSIONS","SOFT_PERMISSIONS"});
		    	  }
	    	 }catch(Exception e){
	    		 System.out.println("No hay Administrador de cursos");
	    	}
	    			  
	    }
	    
	    
	    System.out.println("--- SETTING ACCESS PERMISSION TO MODULES ");
	    List<Module> modules = ModuleLocalServiceUtil.getModules(QueryUtil.ALL_POS, QueryUtil.ALL_POS);
		for(Module module : modules){
			try{
				System.out.println("--- MODULE  "+ module.getModuleId());
			    siteMember = RoleLocalServiceUtil.fetchRole(module.getCompanyId(), RoleConstants.SITE_MEMBER);
				if(siteMember!=null){
					ResourcePermissionLocalServiceUtil.setResourcePermissions(module.getCompanyId(), 
			     			Module.class.getName(),ResourceConstants.SCOPE_INDIVIDUAL, String.valueOf(module.getModuleId()),  siteMember.getRoleId(),  new String[]{"ACCESS","VIEW"});
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		 System.out.println("--- END SETTING ACCESS PERMISSION TO MODULES ");
	}
}