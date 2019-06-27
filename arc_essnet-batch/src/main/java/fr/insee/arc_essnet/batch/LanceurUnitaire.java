package fr.insee.arc_essnet.batch;

import fr.insee.arc_essnet.core.factory.ApiServiceFactory;
import fr.insee.arc_essnet.core.model.TypeTraitementPhase;
import fr.insee.arc_essnet.utils.ressourceUtils.PropertiesHandler;


public class LanceurUnitaire {

	/**
	 * 
	 * @param args
	 *            {@code args[0]} : service à invoquer<br/>
	 *            {@code args[1]} : amount of files to be processed<br/>
	 */
	
	public static void main(String[] args) {
		
	    PropertiesHandler properties =new PropertiesHandler();
		String nb=args[1];
		
		ApiServiceFactory.getService(args[0]
				, properties.getBatchArcEnvironment()
				, properties.getBatchExecutionEnvironment()
				, properties.getBatchParametreRepertoire()
				, nb).invokeApi();
	}

}
