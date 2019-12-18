/*
 * Title:        EdgeCloudSim - Edge Orchestrator
 * 
 * Description: 
 * EdgeOrchestrator is an abstract class which is used for selecting VM
 * for each client requests. For those who wants to add a custom 
 * Edge Orchestrator to EdgeCloudSim should extend this class and provide
 * a concreate instance via ScenarioFactory
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_orchestrator;

import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.utils.ETCMatrix;

import java.util.ArrayList;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.edge_client.Task;

public abstract class EdgeOrchestrator {
	protected String policy;
	protected String simScenario;
	protected String schedAlgo;
		
	public EdgeOrchestrator(String _policy, String _simScenario, String _schedAlgo){
		policy = _policy;
		simScenario = _simScenario;
		schedAlgo=_schedAlgo;
	}
	
	/*
	 * initialize edge orchestrator if needed
	 */
	public abstract void initialize();
	
	/*
	 * decides where to offload
	 */
	public abstract int getDeviceToOffload(Task task);
	
	/*
	 * returns proper VM from the related edge orchestrator point of view
	 */
	public abstract EdgeVM getVmToOffload(Task task);
	
	
	public abstract int getTempListSize();
	
	public abstract Task getElementTempList(int indx);
	
	public abstract double deadline(Task task, ETCMatrix b, double slack, int targetBS);

	public String getPolicy() {
		return policy;
	}

	public void setPolicy(String policy) {
		this.policy = policy;
	}
	
	
	
	
	

}
