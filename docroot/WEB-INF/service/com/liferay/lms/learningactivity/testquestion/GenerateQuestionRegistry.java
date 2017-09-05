package com.liferay.lms.learningactivity.testquestion;

import java.util.List;

import com.liferay.lms.model.TestQuestion;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.portlet.PortletClassLoaderUtil;
import com.liferay.portal.kernel.util.ClassLoaderProxy;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.Validator;

public class GenerateQuestionRegistry {

	private static ClassLoader _portletClassLoader;
	private static GenerateQuestion _generateQuestion = new BaseGenerateQuestion();
	protected static final String _portletID = PropsUtil.get("lms.testquestion.generatequestion.portletid"); 
	
	public static GenerateQuestion _getGenerateQuestion(){
    	
    	String className = PropsUtil.get("lms.testquestion.generatequestion.classname");
		try {	
			_generateQuestion = (GenerateQuestion)getPortletClassLoader().loadClass(className).newInstance();
		} catch (ClassNotFoundException e) {
			try {
				ClassLoaderProxy classLoaderProxy = new ClassLoaderProxy(Class.forName(className, true, 
					PortletClassLoaderUtil.getClassLoader(_portletID)).newInstance(), className, 
					PortletClassLoaderUtil.getClassLoader(_portletID));
				_generateQuestion = new GenerateQuestionClp(classLoaderProxy);
			} catch (Throwable throwable) {
			}
		} catch (ClassCastException e) {
			try {
				ClassLoaderProxy classLoaderProxy = new ClassLoaderProxy(Class.forName(className, true, 
						getPortletClassLoader()).newInstance(), className, getPortletClassLoader());
				_generateQuestion = new GenerateQuestionClp(classLoaderProxy);
			} catch (Throwable throwable) {
			}
		} catch (Throwable throwable) {
		}
    	return _generateQuestion;
    }
	
	public List<TestQuestion> generateAleatoryQuestions(long actId, long typeId) throws SystemException {

		_generateQuestion = _getGenerateQuestion();
		return _generateQuestion.generateAleatoryQuestions(actId, typeId);
	}
	
    private static ClassLoader getPortletClassLoader() {
		
    	if( Validator.isNull(_portletClassLoader) ) {
			ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();
			_portletClassLoader = currentClassLoader;
		}
		return _portletClassLoader;
	}
	
}
