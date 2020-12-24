package fr.insee.arc.ws.services.restServices.setRules;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.insee.arc.core.util.LoggerDispatcher;
import fr.insee.arc.utils.dao.PreparedStatementBuilder;
import fr.insee.arc.utils.dao.UtilitaireDao;
import fr.insee.arc.ws.services.restServices.setRules.pojo.SetRulesPojo;

@RestController
public class SetRulesController {
	
	@Autowired
	private LoggerDispatcher loggerDispatcher;
	
    private static final Logger LOGGER = LogManager.getLogger(SetRulesController.class);
	
	@RequestMapping(value = "/setRules/{sandbox}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> changeRulesClient(
			@RequestBody(required = true) SetRulesPojo bodyPojo
	) throws SQLException
	{
		
		if (bodyPojo.targetRule.equals("WAREHOUSE"))
		{
			replaceRulesDAO(bodyPojo,"arc.ihm_entrepot", "id_entrepot");
		}
		
		if (bodyPojo.targetRule.equals("NORM"))
		{
			replaceRulesDAO(bodyPojo,"arc.ihm_norme","id_norme","periodicite");
		}

		
		if (bodyPojo.targetRule.equals("CALENDAR"))
		{
			replaceRulesDAO(bodyPojo,"arc.ihm_calendrier","id_norme","periodicite","validite_inf","validite_sup");
		}
		
		if (bodyPojo.targetRule.equals("RULESET"))
		{
			replaceRulesDAO(bodyPojo,"arc.ihm_jeuderegle", "id_norme", "periodicite", "validite_inf", "validite_sup", "version");
		}
		
		if (bodyPojo.targetRule.equals("LOAD"))
		{
			replaceRulesDAO(bodyPojo,"arc.ihm_chargement_regle", "id_norme", "periodicite", "validite_inf", "validite_sup", "version", "id_regle");
		}
		
		if (bodyPojo.targetRule.equals("CONTROL"))
		{
			replaceRulesDAO(bodyPojo,"arc.ihm_controle_regle", "id_norme", "periodicite", "validite_inf", "validite_sup", "version");
		}
		
		if (bodyPojo.targetRule.equals("STRUCTURIZE"))
		{
			replaceRulesDAO(bodyPojo,"arc.ihm_normage_regle", "id_norme", "periodicite", "validite_inf", "validite_sup", "version");
		}
		
		if (bodyPojo.targetRule.equals("FILTER"))
		{
			replaceRulesDAO(bodyPojo,"arc.ihm_filtrage_regle", "id_norme", "periodicite", "validite_inf", "validite_sup", "version");
		}
		
		if (bodyPojo.targetRule.equals("MAPPING"))
		{
			replaceRulesDAO(bodyPojo,"arc.ihm_mapping_regle", "id_norme", "periodicite", "validite_inf", "validite_sup", "version");
		}
		
		return ResponseEntity.status(HttpStatus.OK).body("OK");

	}
	
	
	/**
	 * replace = delete + insert
	 * @param bodyPojo
	 * @throws SQLException 
	 */
	public void replaceRulesDAO(SetRulesPojo bodyPojo, String tablename, String...primaryKeys) throws SQLException
	{
		PreparedStatementBuilder requete=new PreparedStatementBuilder();
	
		requete.append(deleteRulesQuery(bodyPojo, tablename, primaryKeys));
		requete.append(insertRulesQuery(bodyPojo, tablename, primaryKeys));
		
		UtilitaireDao.get("arc").executeRequest(null, requete);
	}
	
	
	/**
	 * Query to insert rules
	 * @param bodyPojo
	 * @param tablename
	 * @param primaryKeys
	 * @return
	 */
	public PreparedStatementBuilder insertRulesQuery(SetRulesPojo bodyPojo, String tablename, String...primaryKeys)
	{
		PreparedStatementBuilder requete=new PreparedStatementBuilder();
		List<String> columns=new ArrayList<>(bodyPojo.content.keySet());

		
		// fetch data to insert
		for (int i=0;i<bodyPojo.content.get(columns.get(0)).getData().size();i++)
		{
			requete.append("\n INSERT INTO "+tablename+" (");
			requete.append(String.join(", ", columns));
			requete.append(")");
			requete.append("\n  VALUES (");
			
			boolean first=true;
			
			for (String col:columns)
			{
				if (first)
				{
					first=false;
				}
				else
				{
					requete.append(",");
				}
				requete.append(requete.quoteText(bodyPojo.content.get(col).getData().get(i)));
			}
			
			requete.append(");");
		}
		return requete;
	}
	
	/**
	 * Query to delete rules
	 * @param bodyPojo
	 * @param tablename
	 * @param primaryKeys
	 * @return
	 */
	public PreparedStatementBuilder deleteRulesQuery(SetRulesPojo bodyPojo, String tablename, String...primaryKeys)
	{
		PreparedStatementBuilder requete=new PreparedStatementBuilder();
		List<String> columns=new ArrayList<>(bodyPojo.content.keySet());
		
		for (int i=0;i<bodyPojo.content.get(columns.get(0)).getData().size();i++)
		{
			requete.append("\n DELETE FROM "+tablename+" ");
			
			for (String pk:primaryKeys)
			{
				requete.append("\n WHERE "+pk+" ="+requete.quoteText(bodyPojo.content.get(pk).getData().get(i))+";");
			}
		}

		return requete;
	}
	
}
