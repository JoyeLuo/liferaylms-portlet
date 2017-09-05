package com.liferay.lms.learningactivity.testquestion;

import java.util.List;

import com.liferay.lms.model.TestQuestion;
import com.liferay.portal.kernel.exception.SystemException;

public interface GenerateQuestion {

	/**
	 * Este servicio genera el listado de preguntas en funcion del tipo de actividad de forma aleatoria.
	 * Se utiliza cuando se ha seleccionado el mecanismo de seleccion de preguntas de bancos.
	 * 
	 * @param actId
	 * @param typeId
	 * @return
	 * @throws SystemException
	 */
	public List<TestQuestion> generateAleatoryQuestions(long actId, long typeId) throws SystemException;
	
}
