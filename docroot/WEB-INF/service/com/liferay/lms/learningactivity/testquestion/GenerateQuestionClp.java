package com.liferay.lms.learningactivity.testquestion;

import java.lang.reflect.Method;
import java.util.List;

import com.liferay.lms.learningactivity.questiontype.QuestionType;
import com.liferay.lms.model.TestQuestion;
import com.liferay.lms.service.ClpSerializer;
import com.liferay.portal.kernel.util.ClassLoaderProxy;
import com.liferay.portal.kernel.util.MethodHandler;

public class GenerateQuestionClp implements GenerateQuestion{

	private ClassLoaderProxy clp;
	
	public GenerateQuestionClp(ClassLoaderProxy clp) {
		
		this.clp = clp;
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<TestQuestion> generateAleatoryQuestions(long actId, long typeId) {
		
		Object returnObj = null;
		
		try {
			Method method = QuestionType.class.getMethod("generateAleatoryQuestions", Long.class, Long.class); 
			returnObj = clp.invoke(new MethodHandler(method, actId, typeId));
		}
		catch (Throwable t) {
			t = ClpSerializer.translateThrowable(t);

			if (t instanceof RuntimeException) {
				throw (RuntimeException)t;
			}
			else {
				throw new RuntimeException(t.getClass().getName() +
					" is not a valid exception");
			}
		}
		return ((List<TestQuestion>)returnObj);
	}
	
}
