package com.liferay.lms.learningactivity.testquestion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.liferay.lms.model.TestQuestion;
import com.liferay.lms.service.LearningActivityLocalServiceUtil;
import com.liferay.lms.service.TestQuestionLocalServiceUtil;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portlet.asset.model.AssetEntry;
import com.liferay.portlet.asset.service.AssetEntryLocalServiceUtil;
import com.liferay.portlet.asset.service.persistence.AssetEntryQuery;

public class BaseGenerateQuestion implements GenerateQuestion{

	@Override
	public List<TestQuestion> generateAleatoryQuestions(long actId, long typeId) throws SystemException {
		
		boolean isMultiple = StringPool.TRUE.equals(LearningActivityLocalServiceUtil.getExtraContentValue(actId,"isMultiple"));
		long[] assetCategoryIds = GetterUtil.getLongValues(StringUtil.split(LearningActivityLocalServiceUtil.getExtraContentValue(actId,"categoriesId")));
		long[] classTypeIds = new long[]{typeId};
		int maxQuestions = GetterUtil.getInteger(LearningActivityLocalServiceUtil.getExtraContentValue(actId,"random"), 0);
		
		AssetEntryQuery entryQuery = new AssetEntryQuery();
		entryQuery.getAllCategoryIds();
		List<AssetEntry> banks= new ArrayList<AssetEntry>();
		List<TestQuestion> questions = new ArrayList<TestQuestion>();
		List<TestQuestion> sortQuestions = new ArrayList<TestQuestion>();
		
		if(!Validator.equals(assetCategoryIds.length, 0)){
			
			entryQuery.setAllCategoryIds(assetCategoryIds);
			entryQuery.setClassTypeIds(classTypeIds);
			entryQuery.setVisible(true);
			banks.addAll(AssetEntryLocalServiceUtil.getEntries(entryQuery));

			if(!Validator.equals(banks.size(), 0)){
				if (isMultiple){
					for (AssetEntry bank : banks){
						questions.addAll(TestQuestionLocalServiceUtil.getQuestions(bank.getClassPK()));
						Collections.shuffle(questions);
					}
				}else{
					Collections.shuffle(banks);
					questions = TestQuestionLocalServiceUtil.getQuestions(banks.get(0).getClassPK());
				}
				
				List<TestQuestion> questionsCopy = new ArrayList<TestQuestion>(questions);
				while ( ( maxQuestions > sortQuestions.size() && questionsCopy.size() > 0) || ( maxQuestions == 0 && questionsCopy.size() > 0 ) ) {
					sortQuestions.add(questionsCopy.get(0));
					questionsCopy.remove(0);
				}
				
				return sortQuestions;
			}
		}
		
		return sortQuestions;
	}
	
	
}