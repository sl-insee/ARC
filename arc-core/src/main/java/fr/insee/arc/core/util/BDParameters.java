package fr.insee.arc.core.util;

import java.sql.Connection;
import java.sql.SQLException;

import fr.insee.arc.utils.dao.UtilitaireDao;
import fr.insee.arc.utils.utils.FormatSQL;

public class BDParameters {

	private static final String parameterTable="arc.parameter";
	private static final String getParameterQuery = "SELECT val FROM "+parameterTable+" ";

	private static String parameterQuery(String key) {
		return getParameterQuery + " WHERE key='" + key + "'";
	}

	public static String getString(Connection c, String key) {
		String r = null;
		try {
			r = UtilitaireDao.get("arc").getString(c, parameterQuery(key));
//			System.out.println(">>>>> " + key + " : " + r);
		} catch (Exception e) {
	        // Création de la table de parametre
			StringBuilder requete=new StringBuilder();
			
	        requete.append("\n CREATE SCHEMA IF NOT EXISTS arc; ");
			
	        requete.append("\n CREATE TABLE IF NOT EXISTS arc.parameter ");
	        requete.append("\n ( ");
	        requete.append("\n key text, ");
	        requete.append("\n val text, ");
	        requete.append("\n CONSTRAINT parameter_pkey PRIMARY KEY (key) ");
	        requete.append("\n ); ");
	        
	        
	        try {
				UtilitaireDao.get("arc").executeImmediate(c, requete);
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		return r;
	}

	public static String getString(Connection c, String key, String defaultValue) {
		String s = getString(c, key);
		if (s==null)
		{
			insertDefaultValue(c, key, defaultValue);
		}
		return s == null ? defaultValue : s;
	}

	public static Integer getInt(Connection c, String key) {
		String val=getString(c, key);
		return val==null?null:Integer.parseInt(val);
	}

	public static Integer getInt(Connection c, String key, Integer defaultValue) {
		Integer s = getInt(c, key);
		if (s==null)
		{
			insertDefaultValue(c, key, ""+defaultValue);
		}
		return s == null ? defaultValue : s;
	}
	
	public static void insertDefaultValue(Connection c,String key, String defaultValue)
	{
		try {
			UtilitaireDao.get("arc").executeImmediate(c,"INSERT INTO "+parameterTable+" values ('"+key+"','"+defaultValue+"');");
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}