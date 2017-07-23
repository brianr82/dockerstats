package docker.rest;
//comments with 'ADAM' were modifications made by me. I will mark any changes
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;



//import javax.ws.rs.ServerErrorException;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import docker.model.Container;
import docker.model.ContainerStats;
import docker.api.exceptions.ContainerException;
import docker.api.exceptions.ContainerNotFoundException;
import docker.api.exceptions.ServerErrorException;
import docker.api.exceptions.UnknownErrorException;


public class DocRestClient
{
	// Yar was here!
	//Adam was here too!
	
	
	
    private String m_strEndPoint = "http://52.23.168.200:4000/v1.22";
    private JsonParser theJsonParser = new JsonParser();
    Gson gson = new Gson();
    Map<String, String> mapDocTemplate = new HashMap<>();

    public DocRestClient(String strHost)
    {
        this.m_strEndPoint = "http://" + strHost;
    }

    private String GetDocTemplate(String strTemplate)
    {
        if (this.mapDocTemplate.containsKey(strTemplate) == false)
        {
            // load template
            StringBuilder stringBuilder = new StringBuilder();
            FileReader fileReader = null;
            BufferedReader bufferedReader = null;

            try
            {
                fileReader = new FileReader("./docker-container-templates/" + strTemplate);
                bufferedReader = new BufferedReader(fileReader);
                char[] buf = new char[23];
                int numRead=0;

                while((numRead = bufferedReader.read(buf)) != -1)
                {
                    String readData = String.valueOf(buf, 0, numRead);
                    stringBuilder.append(readData);
                }
                bufferedReader.close();
                fileReader.close();
            }
            catch (Exception ex) { ex.printStackTrace(); }

            this.mapDocTemplate.put(strTemplate, stringBuilder.toString());
        }
        return this.mapDocTemplate.get(strTemplate);
    }

    public String CreateContainer(String strName, String strTemplate) throws ContainerNotFoundException, ServerErrorException, ContainerException, UnknownErrorException
    {
        String strPayload = this.GetDocTemplate(strTemplate);
        RestCallResult restResult = this.RestCallPost(this.m_strEndPoint + "/containers/create?name=" + strName, strPayload);

        if (restResult.responseCode == 201)
        {
            JsonObject jObj = theJsonParser.parse(restResult.responseBody).getAsJsonObject();
            return jObj.get("Id").getAsString();
        }
        else if (restResult.responseCode == 404)
        {
            throw new ContainerNotFoundException(strName);
        }
        else if (restResult.responseCode == 406)
        {
            throw new ContainerException(strName);
        }
        else if (restResult.responseCode == 500)
        {
            throw new ServerErrorException("Server error while creating container [" + strName + "].");
        }
        else
        {
            // unknown response code
            throw new UnknownErrorException("Unknown response code [" + restResult.responseCode + "] from server while creating container [" + strName + "]."); 
        }
    }

    public void StartContainer(String strName) throws ContainerNotFoundException, ServerErrorException, UnknownErrorException
    {
        RestCallResult restResult = this.RestCallPost(this.m_strEndPoint + "/containers/" + strName + "/start", null);

        if (restResult.responseCode == 204 || restResult.responseCode == 304)
        {
            return;
        }
        else if (restResult.responseCode == 404)
        {
            throw new ContainerNotFoundException(strName);
        }
        else if (restResult.responseCode == 500)
        {
            throw new ServerErrorException("Server error while creating container [" + strName + "].");
        }
        else
        {
            // unknown response code
            throw new UnknownErrorException("Unknown response code [" + restResult.responseCode + "] from server while creating container [" + strName + "]."); 
        }
    }

    public String RunContainer(String strName, String strTemplate) throws ContainerNotFoundException, ContainerException, ServerErrorException, UnknownErrorException
    {
        // starting a container could fail if the VM is very new. If we retry, there is a chance that the creating actually succeeds.
        String strId = null;
        int attempt = 0;
        int attemptsMax = 5;
        long timeBetweenAttempts = 1000;
        while (attempt < attemptsMax)
        {
            try
            {
                strId = this.CreateContainer(strName, strTemplate);
                break;
            }
            catch (ContainerNotFoundException | ContainerException | UnknownErrorException | ServerErrorException e)
            {
                ++attempt;
                if (attempt >= attemptsMax)
                {
                    // done with the attempt
                    throw e;
                }
                try
                {
                    // wait a little before a new attempt.
                    Thread.sleep(timeBetweenAttempts);
                }
                catch (InterruptedException e1) { }
            }
        }

        // if we reached this point, then the creation of the container succeeded.
        this.StartContainer(strName);
        return strId;
    }

    public void RestartContainer(String strName) throws ContainerNotFoundException, ServerErrorException, UnknownErrorException
    {
        RestCallResult restResult = this.RestCallPost(this.m_strEndPoint + "/containers/" + strName + "/restart", null);
        if (restResult.responseCode == 204)
        {
            return;
        }
        else if (restResult.responseCode == 404)
        {
            throw new ContainerNotFoundException(strName);
        }
        else if (restResult.responseCode == 500)
        {
            throw new ServerErrorException("Server error while restarting container [" + strName + "].");
        }
        else
        {
            // unknown response code
            throw new UnknownErrorException("Unknown response code [" + restResult.responseCode + "] from server while restarting container [" + strName + "]."); 
        }
    }

    public void RemoveContainer(String strName) throws ContainerException, ContainerNotFoundException, ServerErrorException, UnknownErrorException
    {
        RestCallResult restResult = this.RestCallDelete(this.m_strEndPoint + "/containers/" + strName + "?v=1&force=1");

        if (restResult.responseCode == 204)
        {
            return;
        }
        else if (restResult.responseCode == 400)
        {
            throw new ContainerException(strName);
        }
        else if (restResult.responseCode == 404)
        {
            throw new ContainerNotFoundException(strName);
        }
        else if (restResult.responseCode == 500)
        {
            throw new ServerErrorException("Server error while creating container [" + strName + "].");
        }
        else
        {
            // unknown response code
            throw new UnknownErrorException("Unknown response code [" + restResult.responseCode + "] from server while creating container [" + strName + "]."); 
        }
    }
   
    
    public ContainerStats GetStats(String strContainer) throws ServerErrorException, ContainerNotFoundException, UnknownErrorException
    {
        RestCallResult restResult = RestCallGet(this.m_strEndPoint + "/containers/" + strContainer + "/stats?stream=false");
        if (restResult.responseCode == 200)
        {
            return gson.fromJson(restResult.responseBody, ContainerStats.class);
        }
        else if (restResult.responseCode == 404)
        {
            throw new ContainerNotFoundException(strContainer);
        }
        else if (restResult.responseCode == 500)
        {
            throw new ServerErrorException("Server error while getting");
        }
        else
        {
            // unknown response code
            throw new UnknownErrorException("Unknown response code [" + restResult.responseCode + "] from server while getting statistics for container [" + strContainer + "]."); 
        }
    }

    public String ExecCreate(String strContainerName, String strScript) throws ContainerNotFoundException, ContainerException, ServerErrorException, UnknownErrorException
    {
        String strPayload = ""
                + "{"
                + "\"AttachStdin\": false,"
                + "\"AttachStdout\": true,"
                + "\"AttachStderr\": true,"
                //+ "\"DetachKeys\": \"ctrl-p,ctrl-q\""
                + "\"Tty\": false,"
                + "\"Cmd\": [\"/bin/sh\", \"-c\", \""
                + strScript
                + "\"]"
                + "}";

        RestCallResult restResult = this.RestCallPost(this.m_strEndPoint + "/containers/" + strContainerName + "/exec", strPayload);

        if (restResult.responseCode == 201)
        {
            JsonObject jObj = theJsonParser.parse(restResult.responseBody).getAsJsonObject();
            return jObj.get("Id").getAsString();
        }
        else if (restResult.responseCode == 404)
        {
            throw new ContainerNotFoundException(strContainerName);
        }
        else if (restResult.responseCode == 409)
        {
            throw new ContainerException("Container [" + strContainerName + "] is paused.");
        }
        else if (restResult.responseCode == 500)
        {
            throw new ServerErrorException("Server error while creating exec in container [" + strContainerName + "].");
        }
        else
        {
            // unknown response code
            throw new UnknownErrorException("Unknown response code [" + restResult.responseCode + "] from server while creating exec [" + strContainerName + "]."); 
        }

    }

    public String ExecStart(String strId) throws ContainerNotFoundException, ContainerException, ServerErrorException, UnknownErrorException
    {
        String strPayload = ""
                + "{"
                + "\"Detach\": false,"
                + "\"Tty\": false"
                + "}";

        RestCallResult restResult = this.RestCallPost(this.m_strEndPoint + "/exec/" + strId + "/start", strPayload);
        if (restResult.responseCode == 200)
        {
            return restResult.responseBody;
        }
        else if (restResult.responseCode == 404)
        {
            throw new ContainerNotFoundException(strId);
        }
        else if (restResult.responseCode == 409)
        {
            throw new ContainerException("Container is paused.");
        }
        else if (restResult.responseCode == 500)
        {
            throw new ServerErrorException("Server error while executing the commnad [" + strId + "].");
        }
        else
        {
            // unknown response code
            throw new UnknownErrorException("Unknown response code [" + restResult.responseCode + "] from server while executing command [" + strId + "]."); 
        }

    }

    public String Exec(String strContainerName, String strScript) throws ContainerNotFoundException, ContainerException, ServerErrorException, UnknownErrorException
    {
        String strId = null;
        strId = this.ExecCreate(strContainerName, strScript);
        return this.ExecStart(strId);
    }

    private RestCallResult RestCallDelete(String strRestUrl)
    {
        RestCallResult result = new RestCallResult();

        try
        {
            URL url = new URL(strRestUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("Content-Type", "application/json");

            result.responseCode = conn.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String output;
            while ((output = br.readLine()) != null)
            {
                result.responseBody += output;
            }

            conn.disconnect();
        }
        catch (Exception e) {  }

        return result;
    }

    private RestCallResult RestCallPost(String strRestUrl, String strPayload)
    {
        RestCallResult result = new RestCallResult();

        try
        {
            URL url = new URL(strRestUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            if (strPayload != null)
            {
                OutputStream os = conn.getOutputStream();
                os.write(strPayload.getBytes());
                os.flush();
            }

            result.responseCode = conn.getResponseCode();

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));

            String output;
            while ((output = br.readLine()) != null)
            {
                result.responseBody += output;
            }

            conn.disconnect();
        }
        catch (Exception e) {  }

        return result;
    }

    private RestCallResult RestCallGet(String strRestUrl)
    {
        RestCallResult result = new RestCallResult();

        try
        {
            URL url = new URL(strRestUrl);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            result.responseCode = conn.getResponseCode();

            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

            String output;
            while ((output = br.readLine()) != null)
            {
                result.responseBody += output;
            }

            conn.disconnect();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        return result;
    }

    private class RestCallResult
    {
        int responseCode = 0;
        String responseBody = "";
    }

    public Container[] GetContainers()
    {
        RestCallResult restResult = RestCallGet(this.m_strEndPoint + "/containers/json");

        if (restResult.responseCode == 200)
        {
            return gson.fromJson(restResult.responseBody, Container[].class);
        }
        return new Container[0];
    }
    /////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////// BELOW IS FOR WEB SERVER CONTAINERS /////////
    /////////////////////////////////////////////////////////////////////////////////////
    
    //%% if you have a number of containers: web-worker-01, web-worker-02, web-worker-03 ... this method will choose the highest one: web-worker-03 .. pass this to the RemoveContainer method
    
    public String getLastContainer(){
   	 RestCallResult restResult = RestCallGet(this.m_strEndPoint + "/containers/json");
   	 Container[] container = gson.fromJson(restResult.responseBody, Container[].class);
   	 	ArrayList names = new ArrayList();
   	 	ArrayList numbers = new ArrayList();
   	 	String lastContainerName = "";
   	 	for(int i=0; i<container.length;i++)
   	 	{
   	 		//get the ith name
   	 		String sNames = container[i].strNames[0];	
 
   	 		
//******** Added the following to try and get parse out the proper web worker names
   	 		
   	 	String str = "web";
		int location = 0;
		if (sNames.toLowerCase().contains(str))
			location = sNames.indexOf('w');
	//	String formattedName = sNames.substring(17); // pull out only the container name
	 		
		String formattedName = sNames.substring(location);
//**********
		
 //  		String formattedName = sNames.substring(17); // pull out only the container name
   	 		String currentNumber = "";
   	 		if (formattedName.toLowerCase().contains("web-worker")) // find only the web workers
				numbers.add(currentNumber = formattedName.substring(11,13)); // store the web worker numbers into an ArrayList that can be sorted
   	 	}	
		Collections.sort(numbers);
		lastContainerName= "web-worker-" + numbers.get(numbers.size()-1);
	System.out.println("The last container to be added, and the candidate for removal is: " + lastContainerName);
   	 	return lastContainerName;
   }
    
        
    public ArrayList getWebWorkerNames(){
      	 RestCallResult restResult = RestCallGet(this.m_strEndPoint + "/containers/json");
      	 Container[] container = gson.fromJson(restResult.responseBody, Container[].class);
      	 	ArrayList names = new ArrayList();
      	 	ArrayList webWorkers = new ArrayList();
      	 	String lastContainerName = "";
      	 	for(int i=0; i<container.length;i++)
      	 	{
      	 		String sNames = container[i].strNames[0];	
/**ADAM: trying to troubleshoot on AWS  **/ //System.out.println("The unformatted name is: " + sNames);      	 		
				String str = "web";
				int location = 0;
				if (sNames.toLowerCase().contains(str))
					location = sNames.indexOf('w');
			//	String formattedName = sNames.substring(17); // pull out only the container name
      	 		
				String formattedName = sNames.substring(location);
				String currentNumber = "";
      	 		if (formattedName.toLowerCase().contains("web-worker")) // find only the web workers
   				webWorkers.add(currentNumber = formattedName); // store the web worker numbers into an ArrayList that can be sorted	
      	 	}
      	 System.out.println("The current active Web-Workers are: " + webWorkers);
      	 return webWorkers;	
    }
    
    public ArrayList getAllContainerNames(){
     	 RestCallResult restResult = RestCallGet(this.m_strEndPoint + "/containers/json");
     	 Container[] container = gson.fromJson(restResult.responseBody, Container[].class);
     	 	ArrayList names = new ArrayList();
     	 	ArrayList containerNames = new ArrayList();
     	 	String lastContainerName = "";
     	 	for(int i=0; i<container.length;i++)
     	 	{
     	 		String sNames = container[i].strNames[0];	
/**ADAM: trying to troubleshoot on AWS  **/// System.out.println("The unformatted name is: " + sNames);      	 		
				String str = "web";
				int location = 0;
				
			//	location = sNames.indexOf('/');
			//	String formattedName = sNames.substring(17); // pull out only the container name
     	 		
				String formattedName = sNames.substring(location);
				String currentNumber = "";
     	 		containerNames.add(currentNumber = formattedName); // store the web worker numbers into an ArrayList that can be sorted	
     	 	}
     	 System.out.println("The current active Web-Workers are: " + containerNames);
     	 return containerNames;	
   }
    
    //Per Container
   public String getMemory() throws ServerErrorException, ContainerNotFoundException, UnknownErrorException{
	   ArrayList webWorkers = this.getAllContainerNames();
	   String memory = "";
	   for(int i =0; i<webWorkers.size(); i++)
	   {
	   	ContainerStats currentStat = this.GetStats(webWorkers.get(i).toString());
	   	memory = memory  + webWorkers.get(i).toString() + " ," +  currentStat.memory.usage + "," ; 
	   	 
	   }
	   return memory;
   }
   
   
  public String getCPUperiods () throws ServerErrorException, ContainerNotFoundException, UnknownErrorException{
	  ArrayList webWorkers = this.getAllContainerNames();
	   String periods = " ";
	   for(int i =0; i<webWorkers.size(); i++)
	   {
	   	ContainerStats currentStat = this.GetStats(webWorkers.get(i).toString());
	   	periods = periods + webWorkers.get(i).toString() + "  ," +  currentStat.cpuStatsPrevious.cpuThrottling.throttlingPeriods + ", " ; 
	   	 
	   }
	   return periods;
   }
  public String getThrottledPeriods () throws ServerErrorException, ContainerNotFoundException, UnknownErrorException{
	  ArrayList webWorkers = this.getAllContainerNames();
	   String periods = "";
	   for(int i =0; i<webWorkers.size() ; i++)
	   {
	   	ContainerStats currentStat = this.GetStats(webWorkers.get(i).toString());
	   	periods = periods + webWorkers.get(i).toString() + " ," +  currentStat.cpuStatsPrevious.cpuThrottling.throttledPeriods + ", " ; 
	   	 
	   }
	   return periods;
  }
  public String getThrottledCount () throws ServerErrorException, ContainerNotFoundException, UnknownErrorException{
	  ArrayList webWorkers = this.getAllContainerNames();
	   String count = "";
	   for(int i =0; i<webWorkers.size() ; i++)
	   {
	   	ContainerStats currentStat = this.GetStats(webWorkers.get(i).toString());
	   	count = count  + webWorkers.get(i).toString() + " ," +  currentStat.cpuStatsPrevious.cpuThrottling.throttledTime + ", "  ; 
	   	 
	   }
	   
	   return count;
  }
  
  public String getIndividualCPUutil () throws ServerErrorException, ContainerNotFoundException, UnknownErrorException{
	  ArrayList webWorkers = this.getAllContainerNames();
	   String cpu = "";
	   
	   for(int i =0; i<webWorkers.size() ; i++)
	   {
		   ContainerStats currentStat = this.GetStats(webWorkers.get(i).toString());
		   double currentCpuUsage = this.CpuUsage(currentStat);
		   cpu = cpu  + webWorkers.get(i).toString() + " ," +  currentCpuUsage + ", "  ; 
	   	 
	   }
	   
	   return cpu;
  }
   public double CpuUsage(ContainerStats stats)
   {
	   
	   
	   long cpuSystemUsage = stats.cpuStatsCurrent.cpuUsageSystem;
       long cpuUsage = stats.cpuStatsCurrent.cpuUsage.usageTotal;
       double cpuUtilization = 100.0 * (cpuUsage       - stats.cpuStatsPrevious.cpuUsage.usageTotal)
                                     / (cpuSystemUsage - stats.cpuStatsPrevious.cpuUsageSystem);
       //System.out.println("CPU Utilization for container [dataop.web-worker-01] is [" + cpuUtilization + "]."); // ADAM NOTE: there was an error here, but I just moved the period within the bracket.
       
      // System.out.println(cpuUsage);
	   System.out.println(cpuUtilization);
	   cpuUtilization = cpuUtilization * 4;
	   return cpuUtilization;
   }
   
  //%% Try and write a static method that will calculate the avg CPU usage... the tricky part here is that you need to pass in the instance of DocRestClient, which I am not sure if it is possible..
   //calculate the average CPU usage..

   public double AvgCpuUsage() throws ServerErrorException, ContainerNotFoundException, UnknownErrorException
   {
	   ArrayList webWorkers = this.getWebWorkerNames();
	   ArrayList allStats = new ArrayList();
	   
	   for(int i =0; i<webWorkers.size(); i++)
	   {
	   	ContainerStats currentStat = this.GetStats(webWorkers.get(i).toString());
	   	double currentCpuUsage = this.CpuUsage(currentStat);
	//   	System.out.println("The cpu usage of " + webWorkers.get(i).toString() + " is: " + currentCpuUsage);
	   	allStats.add(currentCpuUsage);
	   }
	   double sum = 0; 
       double averageUsage = 0;
       for(int i=0;i<allStats.size(); i++)
       {
       	sum= sum + (double)allStats.get(i);	
       }
     
       averageUsage = sum/allStats.size();
       
   //    System.out.println("The average utilization of all web-workers is: " + averageUsage );
       return averageUsage;
	   
   }
  
   public void addLoadBalancer() throws InterruptedException{
		  try {
			  
			  this.RunContainer( "load-balancer.service.dataop", "dataop.load-balancer.doctpl");
			  TimeUnit.SECONDS.sleep(30);
			  // theClient.Exec("dataop.load-balancer", "/lb-add-worker.sh" + nextContainer+":9201"); // After calling the RunContainer method, you must call the Exec method
			 // this.Exec("load-balancer.service.dataop", "/lb-add-worker.sh " + nextContainer + ".service.dataop:9201"); // After calling the RunContainer method, you must call the Exec method
		  } catch (ContainerNotFoundException | ContainerException | ServerErrorException | UnknownErrorException e) {
			  // TODO Auto-generated catch block
			  e.printStackTrace();
		  }
   }
  public void addMySQL() throws InterruptedException{
	  try {
		  
		  this.RunContainer( "mysql", "dataop.mysql.doctpl");
		  TimeUnit.SECONDS.sleep(30);
	  } catch (ContainerNotFoundException | ContainerException | ServerErrorException | UnknownErrorException e) {
		  // TODO Auto-generated catch block
		  e.printStackTrace();
	  }
   }
   
   public void addMultiple(int count) throws InterruptedException, ContainerNotFoundException, ContainerException, ServerErrorException, UnknownErrorException
   {   
	   //use getLastContainer and guessNextContainer methods in order to pass in 
	   ArrayList webWorkers = this.getWebWorkerNames();
	   
	   ArrayList <String> addedLBWorkers = new ArrayList <String>();
	   
	   for(int i=1; i<=count;i++) //TODO: if it = 0, then it will still run once... Maybe I need to clean the data before it gets to this point, or first check to see if the count = 8, then do nothing...
	   {
		  webWorkers = this.getWebWorkerNames();// get new values for webWorkers after each pass
		  if (webWorkers.size() <76)
		  {
			  String lastContainer = this.getLastContainer();
			  String nextContainer = this.guessNextContainer(lastContainer);
		   
			  // loop through the webWorkers array, put each webWorker into the RunContainer method
			  try {
				  
				  
				  System.out.println(nextContainer);
				  this.RunContainer( nextContainer + ".service.dataop", "dataop.webapp.doctpl");
				  addedLBWorkers.add(nextContainer);
				 
				 
				  //this.Exec("load-balancer.service.dataop", "/lb-add-worker.sh " + nextContainer + ".service.dataop:9201"); // After calling the RunContainer method, you must call the Exec method
			  } catch (ContainerNotFoundException | ContainerException | ServerErrorException | UnknownErrorException e) {
				  // TODO Auto-generated catch block
				  e.printStackTrace();
			  }
		  }
	   else
		   {
			   System.out.println("Cannot add any additional containers, Max of 67 reached");
			   //TimeUnit.SECONDS.sleep(30);
			 // break;
		   }
	   }
	   TimeUnit.SECONDS.sleep(5);
	   for (int i = 0; i < addedLBWorkers.size(); i ++){
		   this.Exec("load-balancer.service.dataop", "/lb-add-worker.sh " + addedLBWorkers.get(i) + ".service.dataop:9201"); // After calling the RunContainer method, you must call the Exec method
	   }
	   System.out.println(" ----> Timer starts now!");
	   TimeUnit.SECONDS.sleep(35);
	   
}
   
   
   public void removeMultiple(int count) throws InterruptedException
   {   
	   //use getLastContainer
	   ArrayList webWorkers = this.getWebWorkerNames();
	   ArrayList <String> addedLBWorkers = new ArrayList <String>();
	  
		   for(int i=1; i<=count;i++) //TODO: if it = 0, then it will still run once... Maybe I need to clean the data before it gets to this point, or first check to see if the count = 8, then do nothing...
		   {
			   webWorkers = this.getWebWorkerNames();
			   if (webWorkers.size() > 1)
			   {   
			    String lastContainer = this.getLastContainer();
			   // loop through the webWorkers array, put each webWorker into the RunContainer method
			   try {
				   System.out.println("The container to be removed will be: " + lastContainer);
				   // first remove from the load balancer
				   this.Exec("load-balancer.service.dataop", "/lb-rm-worker.sh " +lastContainer+".service.dataop");
				   // wait for it to be removed before actually removing
				   
				   // remove the container
				   this.RemoveContainer( lastContainer + ".service.dataop");
			   } catch (ContainerNotFoundException | ContainerException | ServerErrorException | UnknownErrorException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			
			   }
		   }
			   else
			   {
				   System.out.println("Cannot Remove additional web-workers. Only one remains.");
				  // TimeUnit.SECONDS.sleep(20);
			   		break;
			   }	
			  
		}
		  // TimeUnit.SECONDS.sleep(30);
		   
}
   
    
    public static void main(String ... args) throws ServerErrorException, ContainerNotFoundException, ContainerException, UnknownErrorException// My copy of the main method
, InterruptedException, FileNotFoundException, UnsupportedEncodingException
    {
    	

    	 
    	 
       DocRestClient theClient = new DocRestClient("52.23.168.200:4000");
     

/**       
            PrintWriter writer;
			writer = new PrintWriter("Controller-Results"+ System.currentTimeMillis() +".csv", "UTF-8");
			writer.println("Time,Avg Utilization (100.00),# of Containers");
			//writer.println("The first line");
            //writer.println("The second line");
 
        PidController pid = new PidController();
        // initial values, set outside the loop
        pid.setTunings(.05, 0, 0);
        pid.setpoint = 40;
        boolean exit = false;
        double pidResult = 0; // this is what the pid calculates, initialize to 0 to start
        double value = 0; // this will be some set number to compare to the pidResult... I need to think on this though
        int iterations = 10;
        int rNumber = 0; // this will be the rounded number of the pidResult, rounded down to the whole number
        int removeNum = 0;
        
        int maxCor = 5; // max correction value: if the controller tries to add more than 5 containers at once, correct to this val
        int minCor = 6; // min correction value: if the controller tries to remove more than 6 containers at once, correct to this val
      
        System.out.println("The setpoint is currently " + (pid.setpoint) + "%.");
        for(int i =0; i<iterations; i++)
        {
        	pid.input= theClient.AvgCpuUsage();
        	
        	
        	pid.compute();
        	pidResult =pid.output;
        	rNumber =(int) pidResult;
        	System.out.println("On pass " + i + ", AvgCpuUsage is: " + pid.input + ", pidResult is: " + pidResult + " rNumber is: " + rNumber);
        	writer.println(i +"," + pid.input + "," + theClient.getWebWorkerNames().size());
        	
        	if (pidResult < value)
        	{
        		// do something, such as call removeMultiple(output)
        		
        		
        	//	removeNum = Math.abs(rNumber);
        		System.out.println("The system should remove " + Math.abs(rNumber) + " container(s)");
        	
       
        		theClient.removeMultiple(Math.abs(rNumber));
        		TimeUnit.SECONDS.sleep(5);
        	}
        	else if(pidResult >value)
        	{
        		// do something, such as call addMultiple(output)
        		//System.out.println("The value of the pidResult on pass " + i + " is a positive number: " + pidResult);
        		
        		System.out.println("The system should add " + rNumber + " container(s)");
   
        			
        		theClient.addMultiple(rNumber);
        		TimeUnit.SECONDS.sleep(5);
        	}
        	else if(pidResult == value)
        	{
        		System.out.println("The system has the correct number of container(s)");
        	}
        	
//        	System.out.println("On pass " + i + ", AvgCpuUsage is: " + pid.input + ", pidResult is: " + pidResult + " rNumber is: " + rNumber);
  //      	writer.println(i +"," + pid.input + "," + theClient.getWebWorkerNames().size());
        	
        }
        
        writer.close(); **/ 

   
        //%% Try and start a container... see what happens
 //  theClient.RunContainer("web-worker-01.dataop", "dataop.webapp.doctpl"); // where to get the strScript from? I need to confirm I am using the right script.. check the original guide!
      
        // add to the load balancer
// theClient.Exec("load-balancer.dataop", "/lb-add-worker.sh web-worker-01.dataop:9201");
       
       //remove from the load balancer
 //  theClient.Exec("load-balancer.dataop", "/lb-rm-worker.sh web-worker-01.dataop");
        
   //    remove from docker
//   theClient.RemoveContainer("web-worker-01.dataop");
      

    // The following looks like it works, though I need to change it so I am not passing in 'theClient' but just referring to this.WHATEVERMETHOD
//	theClient.addMultiple(8);

        
    // the following method works to remove containers... tested up to 2 at a time... might need to shorten wait time//
 theClient.removeMultiple(6);
        
        /**
        //% Try and use the getLastContainer method
     String lastContainer = theClient.getLastContainer(); // if you have a number of containers: web-worker-01, web-worker-02, web-worker-03 ... this method will choose the highest one: web-worker-03 .. pass this to the RemoveContainer method
  theClient.getWebWorkerNames();
        
         **/
     //  ArrayList test = theClient.getWebWorkerNames();
       
        
      // String stringLast = theClient.getLastContainer(); 
       System.out.println("Done!"); // keep this in at all times.
    }
    
}


/** _________________________________________________________*********************************_____________________________
_________________________________________________________*********************************_____________________________
_________________________________________________________*********************************_____________________________
_________________________________________________________*********************************_____________________________
_________________________________________________________*********************************_____________________________
_________________________________________________________*********************************_____________________________
**/

/** Leave these out for now... I already have my initial created...
theClient.RunContainer("mysql", "dataop.mysql.doctpl");
theClient.RunContainer("dataop.load-balancer", "dataop.load-balancer.doctpl");
theClient.RunContainer("dataop.web-worker.01", "dataop.webapp.doctpl");
theClient.RunContainer("dataop.web-worker.02", "dataop.webapp.doctpl");

// add to load balancer
theClient.Exec("dataop.load-balancer", "/lb-add-worker.sh dataop.web-worker.01:9201");
theClient.Exec("dataop.load-balancer", "/lb-add-worker.sh dataop.web-worker.02:9201");
**/

// get the containers containers
//    Container[] arrContainers = theClient.GetContainers();
//    System.out.println("The containers are: " + arrContainers + " end of container list");


/** Dont remove... yet
// remove one worker from load balancer, and shutdown the container
theClient.Exec("dataop.load-balancer", "/lb-rm-worker.sh dataop.web-worker.02");
theClient.RemoveContainer("dataop.web-worker.02");
**/

/** Don't shut down for now
// shut down the cluster
theClient.RemoveContainer("mysql");
theClient.RemoveContainer("dataop.load-balancer");
theClient.RemoveContainer("dataop.web-worker.01");
**/



//% Try and use the getLastContainer method
//     String lastContainer = theClient.getLastContainer(); // if you have a number of containers: web-worker-01, web-worker-02, web-worker-03 ... this method will choose the highest one: web-worker-03 .. pass this to the RemoveContainer method


//% Try and use the guessNextContainer
//      String nextContainer = theClient.guessNextContainer(lastContainer); // this method will get the last container, then increment it by 1... eg. if web-worker-03 is the last container added, it will return the string: web-worker-04 

//%% Try the method for calculating CPU usage

//  Double statsTest = CpuUsage(stats);
//System.out.println("The value stored in the Double variable 'statsTest' is: " + statsTest);

//calculate the average CPU usage..

//       double avgCpuUsageFromMethod = theClient.AvgCpuUsage();
//      System.out.println("The average CPU usage as reported by the 'avgCpuUsage' method is: " + avgCpuUsageFromMethod);


/**
ArrayList webWorkers = theClient.getWebWorkerNames();
double avgCpuUsageFromMethod = theClient.AvgCpuUsage();
System.out.println("The average CPU usage as reported by the 'avgCpuUsage' method is: " + avgCpuUsageFromMethod);
**/


/**
	if (removeNum > minCor)
	{
		removeNum = minCor;
		System.out.println("Min Correction Enabled to " + removeNum);
	} 
**/


	/**
if (rNumber > maxCor)
	{
	rNumber = maxCor;
	System.out.println("Max Correction Enabled to " + rNumber);
	}
**/	