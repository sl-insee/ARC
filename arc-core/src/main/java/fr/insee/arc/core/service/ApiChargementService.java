package fr.insee.arc.core.service;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import fr.insee.arc.core.model.TraitementEtat;
import fr.insee.arc.core.service.thread.ThreadChargementService;
import fr.insee.arc.core.util.BDParameters;
import fr.insee.arc.core.util.Norme;
import fr.insee.arc.utils.utils.LoggerDispatcher;


/**
 * ApiChargementService
 *
 * 1- Créer les tables de reception du chargement</br> 2- Récupérer la liste des fichiers à traiter et le nom de leur entrepôt 3- Pour
 * chaque fichier, determiner son format de lecture (zip, tgz, raw) et le chargeur à utlisé (voir entrepot) 4- Pour chaque fichier, Invoquer
 * le chargeur 4-1 Parsing du fichier 4-2 Insertion dans les tables I et A des données lues dans le fichier 4-3 Fin du parsing. Constituer
 * la requete de mise en relation des données chargées et la stocker pour son utilisation ultérieure au normage 5- Fin chargement. Insertion
 * dans la table applicative CHARGEMENT_OK. Mise à jour de la table de pilotage
 *
 * @author Manuel SOULIER
 *
 */



@Component
public class ApiChargementService extends ApiService {
    private static final Logger LOGGER = Logger.getLogger(ApiChargementService.class);
    
    public ApiChargementService() {
        super();
    }
    
    protected String directoryIn;
    private String tableTempA;
    protected String tableTempAll;
    protected String tableChargementOK;
    private String tableChargementBrutal;

    HashMap<String, Integer> col = new HashMap<>();
    private ArrayList<String> allCols;
    private HashMap<String, Integer> colData;
    private StringBuilder requeteInsert;
    protected StringBuilder requeteBilan;
    protected int nbFileLoaded = 0;
    public int start = 0;

    protected String fileName;

    protected List<Norme> listeNorme;
    private int currentIndice;

    private HashMap<String, ArrayList<String>> listIdsource;

    public ApiChargementService(String aCurrentPhase, String anParametersEnvironment, String aEnvExecution, String aDirectoryRoot, Integer aNbEnr,
            String... paramBatch) {
        super(aCurrentPhase, anParametersEnvironment, aEnvExecution, aDirectoryRoot, aNbEnr, paramBatch);

        this.directoryIn = this.getDirectoryRoot() + aEnvExecution.toUpperCase().replace(".", "_") + File.separator + previousPhase + "_"
                + TraitementEtat.OK + File.separator;
        
        // Noms des table temporaires utiles au chargement
        // nom court pour les perfs

        // table A de reception de l'ensemble des fichiers avec nom de colonnes courts
        this.setTableTempA("A");

        // table B de reception de l'ensemble des fichiers brutalement
        this.setTableChargementBrutal("B");

        // table de reception de l'ensemble des fichiers avec nom de colonnes longs
        this.tableTempAll = "L";

        // récupération des différentes normes dans la base
        this.listeNorme = Norme.getNormesBase(this.connexion, this.tableNorme);

    }

    @Override
    public void executer() throws Exception {
        LoggerDispatcher.info("** executer **", LOGGER);
        
        this.MAX_PARALLEL_WORKERS = BDParameters.getInt(this.connexion, "ApiChargementService.MAX_PARALLEL_WORKERS",4);
        
        long dateDebut = java.lang.System.currentTimeMillis() ;

        // maintenance tables : on efface les enregistrement arrivé en mapping ok
        // effacerApresMappingOk(TraitementPhase.MAPPING,
        // TraitementPhase.CHARGEMENT,TraitementPhase.CONTROLE,TraitementPhase.FILTRAGE);

        // Récupérer la liste des fichiers selectionnés
        LoggerDispatcher.info("Récupérer la liste des fichiers selectionnés", LOGGER);
        setListIdsource(pilotageListIdsource(this.tablePilTemp, this.currentPhase, TraitementEtat.ENCOURS.toString()));

        // récupère le nombre de fichier à traiter
        int nbFichier = getListIdsource().get(ID_SOURCE).size();
        
        Connection chargementThread = null;
        ArrayList<ThreadChargementService> threadList = new ArrayList<ThreadChargementService>();
        ArrayList<Connection> connexionList = ApiService.prepareThreads(MAX_PARALLEL_WORKERS, null, this.envExecution);
        currentIndice = 0;

        LoggerDispatcher.info("** Generation des threads pour le chargement **", LOGGER);

        for (currentIndice = 0; currentIndice < nbFichier; currentIndice++) {

            if (currentIndice % 10 == 0) {
                LoggerDispatcher.info("chargement fichier " + currentIndice + "/" + nbFichier + " en "+ (java.lang.System.currentTimeMillis()-dateDebut)+" ms ", LOGGER);
            }

            chargementThread = chooseConnection(chargementThread, threadList, connexionList);

            ThreadChargementService r = new ThreadChargementService(chargementThread, currentIndice, this);
            
            threadList.add(r);
            r.start();
            waitForThreads2(MAX_PARALLEL_WORKERS, threadList, connexionList);

        }

        LoggerDispatcher.info("** Attente de la fin des threads **", LOGGER);
        waitForThreads2(0, threadList, connexionList);


        LoggerDispatcher.info("** Fermeture des connexions **", LOGGER);
        for (Connection connection : connexionList) {
            connection.close();
        }

        LoggerDispatcher.info("****** Fin ApiChargementService *******", LOGGER);
        long dateFin= java.lang.System.currentTimeMillis() ;
        
        LoggerDispatcher.info("Temp chargement des "+ nbFichier+" fichiers : " + (int)Math.round((dateFin-dateDebut)/1000F)+" sec", LOGGER);

    }

    public HashMap<String, ArrayList<String>> getListIdsource() {
        return listIdsource;
    }

    public void setListIdsource(HashMap<String, ArrayList<String>> listIdsource) {
        this.listIdsource = listIdsource;
    }

    public HashMap<String, Integer> getCol() {
        return col;
    }

    public void setCol(HashMap<String, Integer> col) {
        this.col = col;
    }

    public HashMap<String, Integer> getColData() {
        return colData;
    }

    public void setColData(HashMap<String, Integer> colData) {
        this.colData = colData;
    }

    public ArrayList<String> getAllCols() {
        return allCols;
    }

    public void setAllCols(ArrayList<String> allCols) {
        this.allCols = allCols;
    }

    public StringBuilder getRequeteInsert() {
        return requeteInsert;
    }

    public void setRequeteInsert(StringBuilder requeteInsert) {
        this.requeteInsert = requeteInsert;
    }

    public String getTableChargementBrutal() {
        return tableChargementBrutal;
    }

    public void setTableChargementBrutal(String tableChargementBrutal) {
        this.tableChargementBrutal = tableChargementBrutal;
    }

    public String getTableTempA() {
        return tableTempA;
    }

    public void setTableTempA(String tableTempA) {
        this.tableTempA = tableTempA;
    }
}