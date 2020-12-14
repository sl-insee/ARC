package fr.insee.arc.utils.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PreparedStatementBuilder {

private StringBuilder query=new StringBuilder();
	
private List<String> parameters=new ArrayList<String>();


public PreparedStatementBuilder() {
	super();
}


public PreparedStatementBuilder(String query) {
	super();
	this.query.append(query);
}

public PreparedStatementBuilder(StringBuilder query) {
	super();
	this.query = query;
}


public PreparedStatementBuilder append(String s)
{
	query.append(s);
	return this;
}

public PreparedStatementBuilder append(StringBuilder s)
{
	query.append(s);
	return this;
}

@Override
public String toString() {
	
	System.out.println(query);
	Exception e=new Exception();
	e.printStackTrace();	
	
	return null;
}


public PreparedStatementBuilder append(PreparedStatementBuilder s)
{
	query.append(s.query);
	parameters.addAll(s.parameters);
	return this;
}


public int length() {
	return query.length();
}


public void setLength(int i) {
	query.setLength(i);
}


public String sqlEqual(String val, String type) {
    if (val == null) {
        return " is null ";
    } else {
        return " = " + quoteText(val) + " ::" + type+" ";
    }
}

public String quoteText(String s)
{
	parameters.add(s);
	return "?";
}

/**
 * return ?,?,? and add the elements of the list as parameters
 * @param liste
 * @return
 */
public StringBuilder sqlListe(Collection<String> liste)
{
	StringBuilder requete=new StringBuilder();
	
	boolean first=true;
	for (String s:liste)
	{
		if (first)
		{
			first=false;
		}
		else
		{
			requete.append(",");
		}
		requete.append(quoteText(s));
	}
	
	return requete;
}

// getters

public List<String> getParameters() {
	return parameters;
}


public StringBuilder getQuery() {
	return query;
}


public void setQuery(StringBuilder query) {
	this.query = query;
}


public void setParameters(List<String> parameters) {
	this.parameters = parameters;
}




}