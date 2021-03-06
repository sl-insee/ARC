package fr.insee.arc.web.action;

import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.WebApplicationContext;

import fr.insee.arc.utils.dao.PreparedStatementBuilder;
import fr.insee.arc.web.model.MaintenanceParametersModel;
import fr.insee.arc.web.util.VObject;


@Controller
@Scope(scopeName = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class MaintenanceParametersAction extends ArcAction<MaintenanceParametersModel>  {

	private static final String RESULT_SUCCESS = "/jsp/maintenanceParameters.jsp";

	private static final Logger LOGGER = LogManager.getLogger(MaintenanceParametersAction.class);

    private VObject viewParameters;

	@Override
	protected void putAllVObjects(MaintenanceParametersModel arcModel) {
		setViewParameters(this.vObjectService.preInitialize(arcModel.getViewParameters()));
		
		putVObject(getViewParameters(), t -> initializeParameters());
	}

    public void initializeParameters() {
        HashMap<String, String> defaultInputFields = new HashMap<>();
        this.vObjectService.initialize(viewParameters, new PreparedStatementBuilder("SELECT row_number() over (order by description,key,val) as i, key ,val, description FROM arc.parameter"),  "arc.parameter", defaultInputFields);
    }

    @RequestMapping("/selectParameters")
    public String selectParameters(Model model) {

		return generateDisplay(model, RESULT_SUCCESS);
    }

    @RequestMapping("/addParameters")
    public String addParameters(Model model) {
        this.vObjectService.insert(viewParameters);
        return generateDisplay(model, RESULT_SUCCESS);
    }

    @RequestMapping("/deleteParameters")
    public String deleteParameters(Model model) {
         this.vObjectService.delete(viewParameters);
        return generateDisplay(model, RESULT_SUCCESS);
    }

    @RequestMapping("/updateParameters")
    public String updateParameters(Model model) {
        this.vObjectService.update(viewParameters);
        return generateDisplay(model, RESULT_SUCCESS);
    }

    @RequestMapping("/sortParameters")
    public String sortParameters(Model model) {
        this.vObjectService.sort(viewParameters);
        return generateDisplay(model, RESULT_SUCCESS);
    }

    @RequestMapping("/startParameters")
    public String startParameters(Model model) throws Exception {
          	
    	
        return generateDisplay(model, RESULT_SUCCESS);
    }

    public VObject getViewParameters() {
        return this.viewParameters;
    }

    public void setViewParameters(VObject viewParameters) {
        this.viewParameters = viewParameters;
    }


	@Override
	public String getActionName() {
		return "MaintenanceParameters";
	}

    
    
}