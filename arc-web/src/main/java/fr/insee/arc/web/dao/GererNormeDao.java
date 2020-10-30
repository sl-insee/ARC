package fr.insee.arc.web.dao;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import fr.insee.arc.core.model.IDbConstant;
import fr.insee.arc.core.model.JeuDeRegle;
import fr.insee.arc.core.model.RegleMappingEntity;
import fr.insee.arc.core.service.ApiMappingService;
import fr.insee.arc.core.service.engine.mapping.RegleMappingFactory;
import fr.insee.arc.core.service.engine.mapping.VariableMapping;
import fr.insee.arc.core.service.engine.mapping.regles.AbstractRegleMapping;
import fr.insee.arc.utils.dao.EntityDao;
import fr.insee.arc.utils.dao.UtilitaireDao;
import fr.insee.arc.utils.format.Format;
import fr.insee.arc.utils.textUtils.IConstanteCaractere;
import fr.insee.arc.utils.utils.FormatSQL;
import fr.insee.arc.utils.utils.LoggerHelper;
import fr.insee.arc.utils.utils.ManipString;
import fr.insee.arc.utils.utils.SQLExecutor;
import fr.insee.arc.web.action.GererNormeAction;
import fr.insee.arc.web.util.EAlphaNumConstante;
import fr.insee.arc.web.util.VObject;
import fr.insee.arc.web.util.VObjectService;
import fr.insee.arc.web.util.WebLoggerDispatcher;

/**
 * Will own all the utilitary methode used in the {@link GererNormeAction}
 * 
 * @author Pépin Rémi
 *
 */
@Component
public class GererNormeDao implements IDbConstant {

	private static final Logger LOGGER = LogManager.getLogger(GererNormeDao.class);

	private static final String CLEF_CONSOLIDATION = "{clef}";
	public final int INDEX_COLONNE_VARIABLE_TABLE_REGLE_MAPPING = 6;

	private static final String TOKEN_NOM_VARIABLE = "{tokenNomVariable}";

	private static final String MESSAGE_VARIABLE_CLEF_NULL = "La variable {tokenNomVariable} est une variable clef pour la consolidation.\nVous devez vous assurer qu'elle ne soit jamais null.";

    @Autowired
    private WebLoggerDispatcher loggerDispatcher;
    
    @Autowired
	@Qualifier("defaultVObjectService")
    private VObjectService viewObject;

	/**
	 * Return the SQL to get all the rules bond to a rule set. It suppose the a rule
	 * set is selected
	 * 
	 * @param viewRulesSet : the Vobject containing the rules
	 * @param table        : the sql to get the rules in the database
	 * @return an sql query to get all the rules bond to a rule set
	 */
	public String recupRegle(VObject viewRulesSet, String table) {
		StringBuilder requete = new StringBuilder();
		Map<String, ArrayList<String>> selection = viewRulesSet.mapContentSelected();
		HashMap<String, String> type = viewRulesSet.mapHeadersType();
        requete.append("select * from " + table + " ");
        requete.append(" where id_norme" + ManipString.sqlEqual(selection.get("id_norme").get(0), type.get("id_norme")));
        requete.append(" and periodicite" + ManipString.sqlEqual(selection.get("periodicite").get(0), type.get("periodicite")));
        requete.append(" and validite_inf" + ManipString.sqlEqual(selection.get("validite_inf").get(0), type.get("validite_inf")));
        requete.append(" and validite_sup" + ManipString.sqlEqual(selection.get("validite_sup").get(0), type.get("validite_sup")));
        requete.append(" and version" + ManipString.sqlEqual(selection.get("version").get(0), type.get("version")));
		loggerDispatcher.info("donwload request : " + requete.toString(), LOGGER);
		return requete.toString();
	}

	/**
	 * Initialize the {@value GererNormeAction#viewNorme}. Request the full general
	 * norm table.
	 */
	public void initializeViewNorme(VObject viewNorme, String theTableName) {
		LoggerHelper.debug(LOGGER, "/* initializeNorme */");
		HashMap<String, String> defaultInputFields = new HashMap<>();

		viewObject.initialize(
				viewNorme,
				"SELECT id_famille, id_norme, periodicite, def_norme, def_validite, etat FROM arc.ihm_norme order by id_norme", theTableName, defaultInputFields);
	}

	/**
	 * Initialize the {@value GererNormeAction#viewCalendar}. Only get the calendar
	 * link to the selected norm.
	 */
	public void initializeViewCalendar(VObject viewCalendar, VObject viewNorme, String theTableName) {
		LoggerHelper.debug(LOGGER, "/* initializeCalendar */");

		// get the norm selected
		Map<String, ArrayList<String>> selection = viewNorme.mapContentSelected();

		if (!selection.isEmpty()) {
			// Get the type of the column for casting
			HashMap<String, String> type = viewNorme.mapHeadersType();
			// requete de la vue
			StringBuilder requete = new StringBuilder();
			requete.append("select id_norme, periodicite, validite_inf, validite_sup, etat from arc.ihm_calendrier");
			requete.append(
					" where id_norme" + ManipString.sqlEqual(selection.get("id_norme").get(0), type.get("id_norme")));
			requete.append(" and periodicite"
					+ ManipString.sqlEqual(selection.get("periodicite").get(0), type.get("periodicite")));

			// construction des valeurs par défaut pour les ajouts
			HashMap<String, String> defaultInputFields = new HashMap<String, String>();
			defaultInputFields.put("id_norme", selection.get("id_norme").get(0));
			defaultInputFields.put("periodicite", selection.get("periodicite").get(0));

			viewCalendar.setAfterInsertQuery("select arc.fn_check_calendrier(); ");
			viewCalendar.setAfterUpdateQuery("select arc.fn_check_calendrier(); ");

			// Create the vobject
			viewObject.initialize(viewCalendar, requete.toString(), theTableName, defaultInputFields);

		} else {
			viewObject.destroy(viewCalendar);
		}
	}

	/**
	 * Initialize the {@value GererNormeAction#viewRulesSet}. Only get the rulesset
	 * link to the selected norm and calendar.
	 */
	public void initializeViewRulesSet(VObject viewRulesSet, VObject viewCalendar, String theTableName) {
		loggerDispatcher.info("/* initializeViewRulesSet *", LOGGER);

		// Get the selected calendar for requesting the rule set
		Map<String, ArrayList<String>> selection = viewCalendar.mapContentSelected();
		if (!selection.isEmpty()) {
			HashMap<String, String> type = viewCalendar.mapHeadersType();
			StringBuilder requete = new StringBuilder();
			requete.append(
					"select id_norme, periodicite, validite_inf, validite_sup, version, etat from arc.ihm_jeuderegle ");
			requete.append(
					" where id_norme" + ManipString.sqlEqual(selection.get("id_norme").get(0), type.get("id_norme")));
			requete.append(" and periodicite"
					+ ManipString.sqlEqual(selection.get("periodicite").get(0), type.get("periodicite")));
			requete.append(" and validite_inf"
					+ ManipString.sqlEqual(selection.get("validite_inf").get(0), type.get("validite_inf")));
			requete.append(" and validite_sup"
					+ ManipString.sqlEqual(selection.get("validite_sup").get(0), type.get("validite_sup")));

			HashMap<String, String> defaultInputFields = new HashMap<>();
			defaultInputFields.put("id_norme", selection.get("id_norme").get(0));
			defaultInputFields.put("periodicite", selection.get("periodicite").get(0));
			defaultInputFields.put("validite_inf", selection.get("validite_inf").get(0));
			defaultInputFields.put("validite_sup", selection.get("validite_sup").get(0));

			viewRulesSet.setAfterInsertQuery("select arc.fn_check_jeuderegle(); ");
			viewRulesSet.setAfterUpdateQuery("select arc.fn_check_jeuderegle(); ");

			viewObject.initialize(viewRulesSet, requete.toString(), theTableName, defaultInputFields);
		} else {
			viewObject.destroy(viewRulesSet);
		}
	}

	/**
	 * Initialize the the {@link VObjectService} of a load ruleset. Only
	 * get the load rule link to the selected rule set.
	 */
	public void initializeChargement(VObject moduleView, VObject viewRulesSet, String theTableName,
			String scope) {
		loggerDispatcher.info(String.format("Initialize view table %s", theTableName), LOGGER);
		Map<String, ArrayList<String>> selection = viewRulesSet.mapContentSelected();
		if (!selection.isEmpty() && scope != null) {
            HashMap<String, String> type = viewRulesSet.mapHeadersType();
            StringBuilder requete = new StringBuilder();
            requete.append("select id_norme,periodicite,validite_inf,validite_sup,version,id_regle,type_fichier, delimiter, format, commentaire from arc.ihm_chargement_regle");
            requete.append(" where id_norme" + ManipString.sqlEqual(selection.get("id_norme").get(0), type.get("id_norme")));
            requete.append(" and periodicite" + ManipString.sqlEqual(selection.get("periodicite").get(0), type.get("periodicite")));
            requete.append(" and validite_inf" + ManipString.sqlEqual(selection.get("validite_inf").get(0), type.get("validite_inf")));
            requete.append(" and validite_sup" + ManipString.sqlEqual(selection.get("validite_sup").get(0), type.get("validite_sup")));
            requete.append(" and version" + ManipString.sqlEqual(selection.get("version").get(0), type.get("version")));
            HashMap<String, String> defaultInputFields = new HashMap<>();
            defaultInputFields.put("id_norme", selection.get("id_norme").get(0));
            defaultInputFields.put("periodicite", selection.get("periodicite").get(0));
            defaultInputFields.put("validite_inf", selection.get("validite_inf").get(0));
            defaultInputFields.put("validite_sup", selection.get("validite_sup").get(0));
            defaultInputFields.put("version", selection.get("version").get(0));
			viewObject.initialize(moduleView, requete.toString(), theTableName, defaultInputFields);
		} else {
			viewObject.destroy(moduleView);
		}
	}
	
	
	/**
	 * Initialize the the {@link VObjectService} of a load ruleset. Only
	 * get the load rule link to the selected rule set.
	 */
	public void initializeNormage(VObject moduleView, VObject viewRulesSet, String theTableName,
			String scope) {
		loggerDispatcher.info(String.format("Initialize view table %s", theTableName), LOGGER);
		Map<String, ArrayList<String>> selection = viewRulesSet.mapContentSelected();
		if (!selection.isEmpty() && scope != null) {
            HashMap<String, String> type = viewRulesSet.mapHeadersType();
            StringBuilder requete = new StringBuilder();
            requete.append("select id_norme,periodicite,validite_inf,validite_sup,version,id_regle,id_classe,rubrique,rubrique_nmcl,commentaire from arc.ihm_normage_regle");
            requete.append(" where id_norme" + ManipString.sqlEqual(selection.get("id_norme").get(0), type.get("id_norme")));
            requete.append(" and periodicite" + ManipString.sqlEqual(selection.get("periodicite").get(0), type.get("periodicite")));
            requete.append(" and validite_inf" + ManipString.sqlEqual(selection.get("validite_inf").get(0), type.get("validite_inf")));
            requete.append(" and validite_sup" + ManipString.sqlEqual(selection.get("validite_sup").get(0), type.get("validite_sup")));
            requete.append(" and version" + ManipString.sqlEqual(selection.get("version").get(0), type.get("version")));
            HashMap<String, String> defaultInputFields = new HashMap<>();
            defaultInputFields.put("id_norme", selection.get("id_norme").get(0));
            defaultInputFields.put("periodicite", selection.get("periodicite").get(0));
            defaultInputFields.put("validite_inf", selection.get("validite_inf").get(0));
            defaultInputFields.put("validite_sup", selection.get("validite_sup").get(0));
            defaultInputFields.put("version", selection.get("version").get(0));
			viewObject.initialize(moduleView, requete.toString(), theTableName, defaultInputFields);
		} else {
			viewObject.destroy(moduleView);
		}
	}
	
	/**
	 * Initialize the the {@link VObjectService} of a control ruleset. Only
	 * get the load rule link to the selected rule set.
	 */
	public void initializeControle(VObject moduleView, VObject viewRulesSet, String theTableName,
			String scope) {
		loggerDispatcher.info(String.format("Initialize view table %s", theTableName), LOGGER);
		Map<String, ArrayList<String>> selection = viewRulesSet.mapContentSelected();
		if (!selection.isEmpty() && scope != null) {
            HashMap<String, String> type = viewRulesSet.mapHeadersType();
            StringBuilder requete = new StringBuilder();
            requete.append("select id_norme,periodicite,validite_inf,validite_sup,version,id_regle,id_classe,rubrique_pere,rubrique_fils,borne_inf,borne_sup,condition,pre_action,xsd_ordre,xsd_label_fils,xsd_role,commentaire from arc.ihm_controle_regle");
            requete.append(" where id_norme" + ManipString.sqlEqual(selection.get("id_norme").get(0), type.get("id_norme")));
            requete.append(" and periodicite" + ManipString.sqlEqual(selection.get("periodicite").get(0), type.get("periodicite")));
            requete.append(" and validite_inf" + ManipString.sqlEqual(selection.get("validite_inf").get(0), type.get("validite_inf")));
            requete.append(" and validite_sup" + ManipString.sqlEqual(selection.get("validite_sup").get(0), type.get("validite_sup")));
            requete.append(" and version" + ManipString.sqlEqual(selection.get("version").get(0), type.get("version")));
            HashMap<String, String> defaultInputFields = new HashMap<String, String>();
            defaultInputFields.put("id_norme", selection.get("id_norme").get(0));
            defaultInputFields.put("periodicite", selection.get("periodicite").get(0));
            defaultInputFields.put("validite_inf", selection.get("validite_inf").get(0));
            defaultInputFields.put("validite_sup", selection.get("validite_sup").get(0));
            defaultInputFields.put("version", selection.get("version").get(0));
			viewObject.initialize(moduleView, requete.toString(), theTableName, defaultInputFields);
		} else {
			viewObject.destroy(moduleView);
		}
	}

	
	/**
	 * Initialize the the {@link VObjectService} of a filter ruleset. Only
	 * get the load rule link to the selected rule set.
	 */
	public void initializeFiltrage(VObject moduleView, VObject viewRulesSet, String theTableName,
			String scope) {
		loggerDispatcher.info(String.format("Initialize view table %s", theTableName), LOGGER);
		Map<String, ArrayList<String>> selection = viewRulesSet.mapContentSelected();
		if (!selection.isEmpty() && scope != null) {
            HashMap<String, String> type = viewRulesSet.mapHeadersType();
            StringBuilder requete = new StringBuilder();
            requete.append("select * from arc.ihm_filtrage_regle");
            requete.append(" where id_norme" + ManipString.sqlEqual(selection.get("id_norme").get(0), type.get("id_norme")));
            requete.append(" and periodicite" + ManipString.sqlEqual(selection.get("periodicite").get(0), type.get("periodicite")));
            requete.append(" and validite_inf" + ManipString.sqlEqual(selection.get("validite_inf").get(0), type.get("validite_inf")));
            requete.append(" and validite_sup" + ManipString.sqlEqual(selection.get("validite_sup").get(0), type.get("validite_sup")));
            requete.append(" and version" + ManipString.sqlEqual(selection.get("version").get(0), type.get("version")));
            HashMap<String, String> defaultInputFields = new HashMap<String, String>();
            defaultInputFields.put("id_norme", selection.get("id_norme").get(0));
            defaultInputFields.put("periodicite", selection.get("periodicite").get(0));
            defaultInputFields.put("validite_inf", selection.get("validite_inf").get(0));
            defaultInputFields.put("validite_sup", selection.get("validite_sup").get(0));
            defaultInputFields.put("version", selection.get("version").get(0));
			viewObject.initialize(moduleView, requete.toString(), theTableName, defaultInputFields);
		} else {
			viewObject.destroy(moduleView);
		}
	}
	
	/**
	 * Initialize the the {@link VObjectService} of the mapping rule. Only get the load
	 * rule link to the selected rule set.
	 */
	public void initializeMapping(VObject viewMapping, VObject viewRulesSet, String theTableName, String scope) {
		System.out.println("/* initializeMapping */");
		Map<String, ArrayList<String>> selection = viewRulesSet.mapContentSelected();
		if (!selection.isEmpty() && scope != null) {
			
			HashMap<String, String> type = viewRulesSet.mapHeadersType();

			StringBuilder requete = new StringBuilder(
                    "SELECT mapping.id_regle, mapping.id_norme, mapping.validite_inf, mapping.validite_sup, mapping.version, mapping.periodicite, mapping.variable_sortie, mapping.expr_regle_col, mapping.commentaire, variables.type_variable_metier type_sortie /*, variables.nom_table_metier nom_table_metier */ ");
            requete.append("\n  FROM arc.ihm_mapping_regle mapping INNER JOIN arc.ihm_jeuderegle jdr");
            requete.append("\n  ON mapping.id_norme     = jdr.id_norme     AND mapping.periodicite           = jdr.periodicite AND mapping.validite_inf = jdr.validite_inf AND mapping.validite_sup = jdr.validite_sup AND mapping.version = jdr.version");
            requete.append("\n  INNER JOIN arc.ihm_norme norme");
            requete.append("\n  ON norme.id_norme       = jdr.id_norme AND norme.periodicite   = jdr.periodicite");
            requete.append("\n  INNER JOIN (SELECT id_famille, nom_variable_metier, type_variable_metier /*, string_agg(nom_table_metier,',') as nom_table_metier */ FROM arc.ihm_mod_variable_metier group by id_famille, nom_variable_metier, type_variable_metier) variables");
            requete.append("\n  ON variables.id_famille = norme.id_famille AND variables.nom_variable_metier = mapping.variable_sortie");
            requete.append("\n  WHERE mapping.id_norme" + ManipString.sqlEqual(selection.get("id_norme").get(0), type.get("id_norme")));
            requete.append("\n  AND mapping.periodicite" + ManipString.sqlEqual(selection.get("periodicite").get(0), type.get("periodicite")));
            requete.append("\n  AND mapping.validite_inf" + ManipString.sqlEqual(selection.get("validite_inf").get(0), type.get("validite_inf")));
            requete.append("\n  AND mapping.validite_sup" + ManipString.sqlEqual(selection.get("validite_sup").get(0), type.get("validite_sup")));
            requete.append("\n  AND mapping.version" + ManipString.sqlEqual(selection.get("version").get(0), type.get("version")));
            HashMap<String, String> defaultInputFields = new HashMap<String, String>();
            defaultInputFields.put("id_norme", selection.get("id_norme").get(0));
            defaultInputFields.put("periodicite", selection.get("periodicite").get(0));
            defaultInputFields.put("validite_inf", selection.get("validite_inf").get(0));
            defaultInputFields.put("validite_sup", selection.get("validite_sup").get(0));
            defaultInputFields.put("version", selection.get("version").get(0));
            
			viewObject.initialize(viewMapping,requete.toString(),theTableName, defaultInputFields);
		} else {
			viewObject.destroy(viewMapping);
		}
	}

	/**
	 * Initialize the {@value GererNormeAction#viewJeuxDeReglesCopie}. Get in
	 * database all the reccord the rule sets.
	 * 
	 * @param viewJeuxDeReglesCopie
	 */
	public void initializeJeuxDeReglesCopie(VObject viewJeuxDeReglesCopie, VObject viewRulesSet,
			String theTableName, String scope) {
		LoggerHelper.info(LOGGER, "initializeJeuxDeReglesCopie");
		if (scope != null) {
			StringBuilder requete = new StringBuilder();
	        requete.append("select id_norme, periodicite, validite_inf, validite_sup, version, etat from arc.ihm_jeuderegle ");
			HashMap<String, String> defaultInputFields = new HashMap<>();
			viewObject.initialize(viewJeuxDeReglesCopie, requete.toString(), theTableName, defaultInputFields);
		} else {
			viewObject.destroy(viewJeuxDeReglesCopie);
		}

	}

	/**
	 * Send a rule set to production.
	 */
	@SQLExecutor
	public void sendRuleSetToProduction(VObject viewRulesSet, String theTable) {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd:HH");
		Date dNow = new Date();
		loggerDispatcher.warn("Rule set send to production", LOGGER);

		try {
			UtilitaireDao.get("arc").executeRequest(null, "update " + theTable + " set last_init='"
					+ dateFormat.format(dNow) + "', operation=case when operation='R' then 'O' else operation end;");
			viewRulesSet.setMessage("Go to production registered");

		} catch (SQLException e) {
			viewRulesSet.setMessage("Error in the go to production");
			LoggerHelper.warn(LOGGER, "Error in the go to production");

		}
	}

	/**
	 * Create a table to check the update of a rule. Caution to database constrainte
	 *
	 * @param env
	 * @param table
	 * @return
	 */
	public String createTableTempTest(String tableACopier) {

		String nomTableTest = "arc.test_ihm_" + tableACopier;

		StringBuilder create = new StringBuilder();

		create.append("DROP TABLE IF EXISTS " + nomTableTest + "; ");
		create.append("CREATE TABLE " + nomTableTest + " AS SELECT * FROM arc.ihm_" + tableACopier + ";");
		
		create.append("ALTER TABLE " +nomTableTest + " ADD PRIMARY KEY (id_norme, periodicite, validite_inf, validite_sup, version, id_regle);");

		create.append("CREATE CONSTRAINT TRIGGER doublon ");
		create.append("AFTER INSERT OR UPDATE OF rubrique_pere, rubrique_fils ");
		create.append("ON " + nomTableTest + " DEFERRABLE INITIALLY DEFERRED ");
		create.append("FOR EACH ROW ");
		create.append("EXECUTE PROCEDURE arc.verif_doublon(); ");

		create.append("CREATE TRIGGER tg_insert_controle ");
		create.append("before INSERT ON " + nomTableTest + " ");
		create.append("FOR EACH ROW ");
		create.append("EXECUTE PROCEDURE arc.insert_controle(); ");

		return create.toString();
	}

	/**
	 * 
	 * @param viewRulesSet
	 * @return
	 */
	public JeuDeRegle fetchJeuDeRegle(VObject viewRulesSet) {
        HashMap<String, ArrayList<String>> selection = viewRulesSet.mapContentSelected();
        /*
         * Fabrication d'un JeuDeRegle pour conserver les informations sur norme et calendrier
         */
        JeuDeRegle jdr = new JeuDeRegle();
        jdr.setIdNorme(selection.get("id_norme").get(0));
        jdr.setPeriodicite(selection.get("periodicite").get(0));
        jdr.setValiditeInfString(selection.get("validite_inf").get(0), "yyyy-MM-dd");
        jdr.setValiditeSupString(selection.get("validite_sup").get(0), "yyyy-MM-dd");
        jdr.setVersion(selection.get("version").get(0));
        jdr.setEtat(selection.get("etat").get(0));
        return jdr;
	}

	/**
	 * Empty all the rules of a norm modul
	 * 
	 * @param table
	 * @return
	 */
	public void emptyRuleTable(VObject viewRulesSet, String table) {
		loggerDispatcher.info("Empty all the rules of a module", LOGGER);
		Map<String, ArrayList<String>> selection = viewRulesSet.mapContentSelected();
		HashMap<String, String> type = viewRulesSet.mapHeadersType();
		StringBuilder requete = new StringBuilder();
        requete.append("DELETE FROM " + table);
        requete.append(" WHERE id_norme" + ManipString.sqlEqual(selection.get("id_norme").get(0), type.get("id_norme")));
        requete.append(" AND periodicite" + ManipString.sqlEqual(selection.get("periodicite").get(0), type.get("periodicite")));
        requete.append(" AND validite_inf" + ManipString.sqlEqual(selection.get("validite_inf").get(0), type.get("validite_inf")));
        requete.append(" AND validite_sup" + ManipString.sqlEqual(selection.get("validite_sup").get(0), type.get("validite_sup")));
        requete.append(" AND version" + ManipString.sqlEqual(selection.get("version").get(0), type.get("version")));
        requete.append(" ;");

		try {
			UtilitaireDao.get("arc").executeRequest(null, requete);
		} catch (SQLException e) {
			LoggerHelper.error(LOGGER, String.format("Error when emptying the rules %s", e.toString()));

		}
	}

	public String createTableTest(String aNomTable, List<String> listRubrique) {
		 StringBuilder sb = new StringBuilder();
	        sb.append("DROP TABLE IF EXISTS " + aNomTable + ";");
	        sb.append("CREATE TABLE " + aNomTable);
	        sb.append("(id_norme text, periodicite text, id_source text, validite text, id integer, controle text, brokenrules text[] ");
	        
	        //Je m'assure que les ubriques de base ne sont pas dans la liste des rubriques du filtre (sinon requÃªte invalide),
	        //--> cas possible lorsque par exemple, le paramÃ¨tre {validite} apparait dans la rÃ¨gle de filtrage.
	        listRubrique.remove("id_norme");
	        listRubrique.remove("periodicite");
	        listRubrique.remove("id_source");
	        listRubrique.remove("validite");
	        listRubrique.remove("id");
	        listRubrique.remove("controle");
	        listRubrique.remove("brokenrules");

	        
	        for (String rub : listRubrique) {
	            if (rub.toUpperCase().startsWith("I")) {
	                sb.append("," + rub + " integer");
	            } else {
	                sb.append("," + rub + " text");
	            }

	        }
	        sb.append(");");

		loggerDispatcher.info("Creation test table request : " + sb, LOGGER);
		return sb.toString();
	}

	private static List<String> getTableEnvironnement(String state) {
		StringBuilder requete = new StringBuilder();

		String zeEnv = ManipString.substringAfterFirst(state, EAlphaNumConstante.DOT.getValue());
		String zeSchema = ManipString.substringBeforeFirst(state, EAlphaNumConstante.DOT.getValue());

		requete.append("SELECT replace(lower(relname), '" + zeEnv + "', '') ")//
				.append("\n  FROM pg_class a ")//
				.append("\n  INNER JOIN pg_namespace b ON a.relnamespace=b.oid ")//
				.append("\n  WHERE lower(b.nspname)=lower('" + zeSchema + "') ")//
				.append("\n  AND lower(relname) LIKE '" + zeEnv.toLowerCase() + "\\_%'; ");
		return UtilitaireDao.get(poolName).getList(null, requete, new ArrayList<String>());
	}

	/**
	 * @param afterUpdate
	 * @param isRegleOk
	 * @return
	 */
	public boolean testerReglesMapping(VObject viewMapping, VObject viewRulesSet, VObject viewNorme,
			Map<String, ArrayList<String>> afterUpdate) {
		boolean isRegleOk = true;
		List<String> tableADropper = new ArrayList<>();
		String zeExpression = null;
		String zeVariable = null;
		if (!afterUpdate.isEmpty() && !afterUpdate.get("expr_regle_col").isEmpty()) {

			try {
				/*
				 * Récupération du jeu de règle
				 */
				JeuDeRegle jdr = fetchJeuDeRegle(viewRulesSet);
				/*
				 * recopie des tables de l'environnement
				 */
				List<String> tables = getTableEnvironnement(jdr.getEtat());
				List<String> sources = new ArrayList<>();
				List<String> targets = new ArrayList<>();
				String envTarget = jdr.getEtat().replace(EAlphaNumConstante.DOT.getValue(), ".test_");
				for (int i = 0; i < tables.size(); i++) {
					sources.add(jdr.getEtat() + tables.get(i));
					targets.add(envTarget + tables.get(i));
				}
				tableADropper.addAll(targets);

				UtilitaireDao.get(poolName).dupliquerVers(null, sources, targets, "false");
				List<AbstractRegleMapping> listRegle = new ArrayList<>();
				RegleMappingFactory regleMappingFactory = new RegleMappingFactory(null, envTarget,
						new HashSet<String>(), new HashSet<String>());
				String idFamille = viewNorme.mapContentSelected().get("id_famille").get(0);
				regleMappingFactory.setIdFamille(idFamille);
				for (int i = 0; i < afterUpdate.get("expr_regle_col").size(); i++) {
					String expression = afterUpdate.get("expr_regle_col").get(i);
					String variable = afterUpdate.get("variable_sortie").get(i);
					String type = afterUpdate.get("type_sortie").get(i);
					VariableMapping variableMapping = new VariableMapping(regleMappingFactory, variable, type);
					variableMapping.setExpressionRegle(regleMappingFactory.get(expression, variableMapping));
					listRegle.add(variableMapping.getExpressionRegle());
				}
				/*
				 * on dérive pour avoir de belles expressions à tester et connaitre les noms de
				 * colonnes
				 */
				for (int i = 0; i < listRegle.size(); i++) {
					listRegle.get(i).deriverTest();
				}
				Set<Integer> groupesUtiles = groupesUtiles(listRegle);
				/*
				 * on fait remonter les noms de colonnes dans colUtiles
				 */
				Set<String> colUtiles = new TreeSet<>();
				for (int i = 0; i < listRegle.size(); i++) {
					for (Integer groupe : groupesUtiles) {
						colUtiles.addAll(listRegle.get(i).getEnsembleIdentifiantsRubriques(groupe));
						colUtiles.addAll(listRegle.get(i).getEnsembleNomsRubriques(groupe));
					}
					colUtiles.addAll(listRegle.get(i).getEnsembleIdentifiantsRubriques());
					colUtiles.addAll(listRegle.get(i).getEnsembleNomsRubriques());
				}

				createTablePhasePrecedente(envTarget, colUtiles, tableADropper);
				for (int i = 0; i < listRegle.size(); i++) {
					zeExpression = listRegle.get(i).getExpression();
					zeVariable = listRegle.get(i).getVariableMapping().getNomVariable();
					// Test de l'expression
					groupesUtiles = groupesUtiles(Arrays.asList(listRegle.get(i)));
					if (groupesUtiles.isEmpty()) {
						if (createRequeteSelect(envTarget, listRegle.get(i))
								&& CLEF_CONSOLIDATION.equalsIgnoreCase(afterUpdate.get("type_consolidation").get(i))) {
							throw new IllegalArgumentException(MESSAGE_VARIABLE_CLEF_NULL.replace(TOKEN_NOM_VARIABLE,
									listRegle.get(i).getVariableMapping().getNomVariable()));
						}
					} else {
						for (Integer groupe : groupesUtiles) {
							if (createRequeteSelect(envTarget, listRegle.get(i), groupe) && CLEF_CONSOLIDATION
									.equalsIgnoreCase(afterUpdate.get("type_consolidation").get(i))) {
								throw new IllegalArgumentException(MESSAGE_VARIABLE_CLEF_NULL.replace(
										TOKEN_NOM_VARIABLE, listRegle.get(i).getVariableMapping().getNomVariable()
												+ "(groupe " + groupe + ")"));
							}
						}
					}
				}
			} catch (Exception ex) {
				isRegleOk = false;
				LoggerHelper.error(LOGGER, ex, "");
				viewMapping.setMessage((zeVariable == null ? EAlphaNumConstante.EMPTY.getValue()
						: "La règle " + zeVariable + " ::= " + zeExpression + " est erronée.\n") + "Exception levée : "
						+ ex.getMessage());
			} finally {
				UtilitaireDao.get(poolName).dropTable(null, tableADropper.toArray(new String[0]));
			}
		}
		return isRegleOk;
	}

	/**
	 * @param listRegle
	 * @param returned
	 * @return
	 * @throws Exception
	 */
	private static Set<Integer> groupesUtiles(List<AbstractRegleMapping> listRegle) throws Exception {
		Set<Integer> returned = new TreeSet<>();
		for (int i = 0; i < listRegle.size(); i++) {
			returned.addAll(listRegle.get(i).getEnsembleGroupes());
		}
		return returned;
	}

	/**
	 * Exécution d'une règle sur la table <anEnvTarget>_filtrage_ok
	 *
	 * @param anEnvTarget
	 * @param regleMapping
	 * @return
	 * @throws SQLException
	 */
	private static Boolean createRequeteSelect(String anEnvTarget, AbstractRegleMapping regleMapping) throws Exception {
		StringBuilder requete = new StringBuilder("SELECT CASE WHEN ")//
				.append("(" + regleMapping.getExpressionSQL() + ")::" + regleMapping.getVariableMapping().getType())//
				// .append(" IS NULL THEN true ELSE false END")//
				.append(" IS NULL THEN false ELSE false END")//
				.append(" AS " + regleMapping.getVariableMapping().getNomVariable());
		requete.append("\n  FROM " + anEnvTarget + EAlphaNumConstante.UNDERSCORE.getValue() + "filtrage_ok ;");
		return UtilitaireDao.get(poolName).getBoolean(null, requete);
	}

	private static Boolean createRequeteSelect(String anEnvTarget, AbstractRegleMapping regleMapping, Integer groupe)
			throws Exception {
		StringBuilder requete = new StringBuilder("SELECT CASE WHEN ");//
		requete.append("(" + regleMapping.getExpressionSQL(groupe) + ")::"
				+ regleMapping.getVariableMapping().getType().replace("[]", ""))//
				// .append(" IS NULL THEN true ELSE false END")//
				.append(" IS NULL THEN false ELSE false END")//
				.append(" AS " + regleMapping.getVariableMapping().getNomVariable());
		requete.append("\n  FROM " + anEnvTarget + EAlphaNumConstante.UNDERSCORE.getValue() + "filtrage_ok ;");
		return UtilitaireDao.get(poolName).getBoolean(null, requete);
	}

	/**
	 * Creation d'une table vide avec les colonnes adéquates.<br/>
	 * En particulier, si la règle n'utilise pas du tout de noms de colonnes, une
	 * colonne {@code col$null} est créée, qui permette un requêtage.
	 *
	 * @param anEnvTarget
	 * @param colUtiles
	 * @param tableADropper
	 * @throws SQLException
	 */
	private static void createTablePhasePrecedente(String anEnvTarget, Set<String> colUtiles,
			List<String> tableADropper) throws SQLException {
		StringBuilder requete = new StringBuilder(
				"DROP TABLE IF EXISTS " + anEnvTarget + EAlphaNumConstante.UNDERSCORE.getValue() + "filtrage_ok;");
		requete.append("CREATE TABLE " + anEnvTarget + EAlphaNumConstante.UNDERSCORE.getValue() + "filtrage_ok (");
		requete.append(Format.untokenize(colUtiles, " text, "));
		requete.append(colUtiles.isEmpty() ? "col$null text" : " text")//
				.append(");");

		requete.append("\nINSERT INTO " + anEnvTarget + EAlphaNumConstante.UNDERSCORE.getValue() + "filtrage_ok (")//
				.append(Format.untokenize(colUtiles, ", "))//
				.append(colUtiles.isEmpty() ? "col$null" : EAlphaNumConstante.EMPTY.getValue())//
				.append(") VALUES (");
		boolean isFirst = true;
		for (String variable : colUtiles) {
			if (isFirst) {
				isFirst = false;
			} else {
				requete.append(", ");
			}
			if (ApiMappingService.colNeverNull.contains(variable)) {
				requete.append("'" + variable + "'");
			} else {
				requete.append("null");
			}
		}
		requete.append(colUtiles.isEmpty() ? "null" : EAlphaNumConstante.EMPTY.getValue())//
				.append(");");

		tableADropper.add(anEnvTarget + EAlphaNumConstante.UNDERSCORE.getValue() + "filtrage_ok");
		UtilitaireDao.get(poolName).executeRequest(null, requete);
	}

	public void calculerVariableToType(VObject viewNorme, Map<String, String> mapVariableToType,
			Map<String, String> mapVariableToTypeConso) throws SQLException {
		ArrayList<ArrayList<String>> resultat = UtilitaireDao.get("arc").executeRequest(null, new StringBuilder(
				"SELECT DISTINCT lower(nom_variable_metier) AS nom_variable_metier, type_variable_metier, type_consolidation AS type_sortie FROM arc.ihm_mod_variable_metier WHERE id_famille='"
						+ viewNorme.mapContentSelected().get("id_famille").get(0) + "'"));
		for (int i = 2; i < resultat.size(); i++) {
			mapVariableToType.put(resultat.get(i).get(0), resultat.get(i).get(1));
			mapVariableToTypeConso.put(resultat.get(i).get(0), resultat.get(i).get(2));
		}
	}

	public Map<String, ArrayList<String>> calculerReglesAImporter(MultipartFile aFileUpload,
			List<RegleMappingEntity> listeRegle, EntityDao<RegleMappingEntity> dao,
			Map<String, String> mapVariableToType, Map<String, String> mapVariableToTypeConso) throws IOException {
		Map<String, ArrayList<String>> returned = new HashMap<>();
		returned.put("type_sortie", new ArrayList<String>());
		returned.put("type_consolidation", new ArrayList<String>());
		try (BufferedReader br = new BufferedReader(new InputStreamReader(aFileUpload.getInputStream(), StandardCharsets.UTF_8));){
			dao.setSeparator(";");
			String line = br.readLine();
			String someNames = line;
			dao.setNames(someNames);
			line = br.readLine();
			String someTypes = line;
			dao.setTypes(someTypes);
			while ((line = br.readLine()) != null) {
				RegleMappingEntity entity = dao.get(line);
				listeRegle.add(entity);
				for (String colName : entity.colNames()) {
					if (!returned.containsKey(colName)) {
						/*
						 * La colonne n'existe pas encore ? Je l'ajoute.
						 */
						returned.put(colName, new ArrayList<String>());
					}
					/*
					 * J'ajoute la valeur en fin de colonne.
					 */
					returned.get(colName).add(entity.get(colName));
				}
				returned.get("type_sortie").add(mapVariableToType.get(entity.getVariableSortie()));
				returned.get("type_consolidation").add(mapVariableToTypeConso.get(entity.getVariableSortie()));
			}			
		} catch (Exception ex) {
			LoggerHelper.error(LOGGER, "error in calculerReglesAImporter", ex.getStackTrace());
		}
		return returned;
	}

	public boolean testerConsistanceRegleMapping(List<List<String>> data, String anEnvironnement,
			String anIdFamille, StringBuilder aMessage) {
		Set<String> variableRegleCharge = new HashSet<String>();
		for (int i = 0; i < data.size(); i++) {
			variableRegleCharge.add(data.get(i).get(INDEX_COLONNE_VARIABLE_TABLE_REGLE_MAPPING));
		}
		Set<String> variableTableModele = new HashSet<String>();
		variableTableModele
				.addAll(UtilitaireDao.get("arc").getList(null,
						new StringBuilder("SELECT DISTINCT nom_variable_metier FROM " + anEnvironnement
								+ "_mod_variable_metier WHERE id_famille='" + anIdFamille + "'"),
						new ArrayList<String>()));
		loggerDispatcher.info(
				"La requete de construction de variableTableMetier : \n" + "SELECT DISTINCT nom_variable_metier FROM "
						+ anEnvironnement + "_mod_variable_metier WHERE id_famille='" + anIdFamille + "'",
				LOGGER);
		Set<String> variableToute = new HashSet<String>();
		variableToute.addAll(variableRegleCharge);
		variableToute.addAll(variableTableModele);
		boolean ok = true;
		loggerDispatcher.info("Les variables du modèle : " + variableTableModele, LOGGER);
		loggerDispatcher.info("Les variables des règles chargées : " + variableRegleCharge, LOGGER);
		for (String variable : variableToute) {
			if (!variableRegleCharge.contains(variable)) {
				ok = false;
				aMessage.append("La variable " + variable + " n'est pas présente dans les règles chargées.\n");
			}
			if (!variableTableModele.contains(variable)) {
				ok = false;
				aMessage.append(variable + " ne correspond à aucune variable existant.\n");
			}
		}
		if (!ok) {
			aMessage.append("Les règles ne seront pas chargées.");
		}
		return ok;
	}

	public String getIdFamille(String anEnvironnement, String anIdNorme) throws SQLException {
		return UtilitaireDao.get("arc").getString(null, "SELECT id_famille FROM " + anEnvironnement + "_norme"
				+ " WHERE AbstractRuleDAO.ID_NORME='" + anIdNorme + "'");
	}

	/**
	 *
	 * @param returned
	 * @param index    -1 : en fin de tableau<br/>
	 *                 [0..size[ : le premier élément est ajouté à l'emplacement
	 *                 {@code index}, les suivants, juste après
	 * @param args
	 * @return
	 */
	public List<List<String>> ajouterInformationTableau(List<List<String>> returned, int index, String... args) {
		for (int i = 0; i < returned.size(); i++) {
			for (int j = 0; j < args.length; j++) {
				returned.get(i).add((index == -1 ? returned.get(i).size() : index + j), args[j]);
			}
		}
		return returned;
	}

	/**
	 * 
	 * @param vObjectToUpdate the vObject to update with file
	 * @param tableName       the
	 */
	@SQLExecutor
	public void uploadFileRule(VObject vObjectToUpdate, VObject viewRulesSet, MultipartFile theFileToUpload) {
		StringBuilder requete = new StringBuilder();

		// Check if there is file
		if (theFileToUpload == null || theFileToUpload.isEmpty()) {
			// No file -> ko
			vObjectToUpdate.setMessage("Please select a file.");
		} else {
			// A file -> can process it
			LoggerHelper.debug(LOGGER, " filesUpload  : " + theFileToUpload);

			// before inserting in the final table, the rules will be inserted in a table to
			// test them
			String nomTableImage = FormatSQL.temporaryTableName(vObjectToUpdate.getTable() + "_img" + 0);
			
			try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(theFileToUpload.getInputStream(), StandardCharsets.UTF_8));) {
				// Get headers
				List<String> listHeaders = getHeaderFromFile(bufferedReader);

				/*
				 * Création d'une table temporaire (qui ne peut pas être TEMPORARY)
				 */
			    requete.append("\n DROP TABLE IF EXISTS " + nomTableImage + " cascade;");
			    requete.append("\n CREATE TABLE " + nomTableImage + " AS SELECT "//
				    + Format.untokenize(listHeaders, ", ") //
				    + "\n\t FROM " //
				    + vObjectToUpdate.getTable() //
				    + "\n\t WHERE false");


				UtilitaireDao.get("arc").executeRequest(null, requete.toString());

				// Throwing away the first line
				bufferedReader.readLine();

				// Importing the file in the database (COPY command)
				UtilitaireDao.get("arc").importing(null, nomTableImage, bufferedReader, true, false,
						IConstanteCaractere.semicolon);

			} catch (Exception ex) {
				vObjectToUpdate.setMessage("Error when uploading the file : " + ex.getMessage());
				LoggerHelper.error(LOGGER, ex, "uploadOutils()", "\n");
				// After the exception, the methode cant go further, so the better thing to do
				// is to quit it
				return;
			}
			LoggerHelper.debug(LOGGER, "Insert file in the " + nomTableImage + " table");

			Map<String, ArrayList<String>> selection = viewRulesSet.mapContentSelected();

			requete.setLength(0);

			requete.append("\n UPDATE " + nomTableImage + " SET ");
			requete.append("\n id_norme='" + selection.get("id_norme").get(0) + "'");
			requete.append("\n, periodicite='" + selection.get("periodicite").get(0) + "'");
			requete.append("\n, validite_inf='" + selection.get("validite_inf").get(0) + "'");
			requete.append("\n, validite_sup='" + selection.get("validite_sup").get(0) + "'");
			requete.append("\n, version='" + selection.get("version").get(0) + "';");
			requete.append("\n DELETE FROM " + vObjectToUpdate.getTable());
			requete.append("\n WHERE ");
			requete.append("\n id_norme='" + selection.get("id_norme").get(0) + "'");
			requete.append("\n AND  periodicite='" + selection.get("periodicite").get(0) + "'");
			requete.append("\n AND  validite_inf='" + selection.get("validite_inf").get(0) + "'");
			requete.append("\n AND  validite_sup='" + selection.get("validite_sup").get(0) + "'");
			requete.append("\n AND  version='" + selection.get("version").get(0) + "';");
			requete.append("\n INSERT INTO " + vObjectToUpdate.getTable() + " ");
			requete.append("\n SELECT * FROM " + nomTableImage + " ;");
			requete.append("\n DROP TABLE IF EXISTS " + nomTableImage + " cascade;");

			try {
				UtilitaireDao.get("arc").executeRequest(null, requete.toString());
			} catch (Exception ex) {
				vObjectToUpdate.setMessage("Error when uploading the file : " + ex.getMessage());
				LoggerHelper.error(LOGGER, ex, "uploadOutils()");
			}
		}

	}

	private static List<String> getHeaderFromFile(BufferedReader bufferedReader) throws IOException {
		String listeColonnesAggregees = bufferedReader.readLine();
		List<String> listeColonnes = Arrays.asList(listeColonnesAggregees.split(IConstanteCaractere.semicolon));
		LoggerHelper.debug(LOGGER, "Columns list : ", Format.untokenize(listeColonnes, ", "));
		return listeColonnes;
	}

}
