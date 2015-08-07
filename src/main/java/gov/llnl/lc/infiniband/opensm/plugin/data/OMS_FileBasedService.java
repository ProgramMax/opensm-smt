/************************************************************
 * Copyright (c) 2015, Lawrence Livermore National Security, LLC.
 * Produced at the Lawrence Livermore National Laboratory.
 * Written by Timothy Meier, meier3@llnl.gov, All rights reserved.
 * LLNL-CODE-673346
 *
 * This file is part of the OpenSM Monitoring Service (OMS) package.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (as published by
 * the Free Software Foundation) version 2.1 dated February 1999.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * OUR NOTICE AND TERMS AND CONDITIONS OF THE GNU GENERAL PUBLIC LICENSE
 *
 * Our Preamble Notice
 *
 * A. This notice is required to be provided under our contract with the U.S.
 * Department of Energy (DOE). This work was produced at the Lawrence Livermore
 * National Laboratory under Contract No.  DE-AC52-07NA27344 with the DOE.
 *
 * B. Neither the United States Government nor Lawrence Livermore National
 * Security, LLC nor any of their employees, makes any warranty, express or
 * implied, or assumes any liability or responsibility for the accuracy,
 * completeness, or usefulness of any information, apparatus, product, or
 * process disclosed, or represents that its use would not infringe privately-
 * owned rights.
 *
 * C. Also, reference herein to any specific commercial products, process, or
 * services by trade name, trademark, manufacturer or otherwise does not
 * necessarily constitute or imply its endorsement, recommendation, or favoring
 * by the United States Government or Lawrence Livermore National Security,
 * LLC. The views and opinions of authors expressed herein do not necessarily
 * state or reflect those of the United States Government or Lawrence Livermore
 * National Security, LLC, and shall not be used for advertising or product
 * endorsement purposes.
 *
 *        file: OMS_FileBasedService.java
 *
 *  Created on: Aug 6, 2015
 *      Author: meier3
 ********************************************************************/
package gov.llnl.lc.infiniband.opensm.plugin.data;

import gov.llnl.lc.infiniband.opensm.plugin.net.OsmSession;
import gov.llnl.lc.logging.CommonLogger;
import gov.llnl.lc.smt.command.SmtCommand;
import gov.llnl.lc.smt.props.SmtProperty;
import gov.llnl.lc.time.TimeStamp;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**********************************************************************
 * Describe purpose and responsibility of OMS_FileBasedService
 * <p>
 * @deprecated use {@link OMS_NFileBasedService()} instead
 * @see  related classes and interfaces
 *
 * @author meier3
 * 
 * @version Mar 22, 2013 12:14:43 PM
 **********************************************************************/
@Deprecated
public class OMS_FileBasedService implements Runnable, CommonLogger, OMS_Updater
{
  /** the synchronization object **/
  private static Boolean semaphore            = new Boolean( true );

  /** the one and only <code>OMS_ConnectionBasedService</code> Singleton **/
  private volatile static OMS_FileBasedService globalUpdateService  = null;

  /** boolean specifying whether the thread should continue **/
  private volatile static boolean Continue_Thread = true;
  
  /** boolean specifying whether the thread has been created **/
  protected static boolean Thread_Exists = false;

  /** boolean specifying whether the thread is running **/
  protected static boolean Thread_Running = false;

  /** thread responsible for getting new server instances **/
  private static java.lang.Thread Listener_Thread;
  
  /** period between getting new instances of the service **/
//  private static long updatePeriod   = 30;
  
  /** the creation date/time of this Updater */
  private static TimeStamp UpTime = null;
  
  /** configuration or initialization properties */
  private static Properties serviceProps = null;
  
  /** an OMS session for obtaining all the information **/
  private static OsmSession ParentSession = null;
  
  private static volatile OpenSmMonitorService osmService = null; 
  private static volatile OMS_Collection omsHistory = null;
  
  private static String filename = null;
  
  private static String host = "localhost";
  
  private static String port = "10011";
  
  private static boolean reuseConnection = false;
  

  /** a list of Listeners, interested in knowing when new service info available **/
  private static java.util.ArrayList <OSM_ServiceChangeListener> Service_Listeners =
    new java.util.ArrayList<OSM_ServiceChangeListener>();

  private OMS_FileBasedService()
  {
     createThread();
  }
  
  /**************************************************************************
   *** Method Name:
   ***     createThread
   **/
   /**
   *** Summary_Description_Of_What_createThread_Does.
   *** <p>
   ***
   *** @see          Method_related_to_this_method
   ***
   *** @param        Parameter_name  Description_of_method_parameter__Delete_if_none
   ***
   *** @return       Description_of_method_return_value__Delete_if_none
   ***
   *** @throws       Class_name  Description_of_exception_thrown__Delete_if_none
   **************************************************************************/

   private void createThread()
   {
     if (!Thread_Exists)
     {
       UpTime = new TimeStamp();

       // set up the thread to listen
       Listener_Thread = new Thread(this);
       Listener_Thread.setDaemon(true);
       Listener_Thread.setName("OMS_FileBasedService");
       
       logger.info("Creating the " + Listener_Thread.getName() + " Thread");

       Thread_Exists = true;
     }
   }

  /**************************************************************************
   *** Method Name:
   ***     startThread
   **/
   /**
   *** Summary_Description_Of_What_startThread_Does.
   *** <p>
   ***
   *** @see          Method_related_to_this_method
   ***
   *** @param        Parameter_name  Description_of_method_parameter__Delete_if_none
   ***
   *** @return       Description_of_method_return_value__Delete_if_none
   ***
   *** @throws       Class_name  Description_of_exception_thrown__Delete_if_none
   **************************************************************************/

   private boolean startThread()
   {
     logger.info("Starting the " + Listener_Thread.getName() + " Thread");
     
     // open the file if possible
     String fn = SmtCommand.convertSpecialFileName(getFile());
     try
    {
      omsHistory = OMS_Collection.readOMS_Collection(fn);
    }
    catch (FileNotFoundException e)
    {
      // TODO Auto-generated catch block
      System.err.println("Couldn't open the file");
      e.printStackTrace();
    }
     
    catch (IOException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (ClassNotFoundException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
     logger.severe("Done reading the file: " + fn);

     
     // don't start the thread until after the file has been read
     Listener_Thread.start();
     return true;
   }
   /*-----------------------------------------------------------------------*/

   /**************************************************************************
    *** Method Name:
    ***     stopThread
    **/
    /**
    *** Summary_Description_Of_What_startThread_Does.
    *** <p>
    ***
    *** @see          Method_related_to_this_method
    ***
    *** @param        Parameter_name  Description_of_method_parameter__Delete_if_none
    ***
    *** @return       Description_of_method_return_value__Delete_if_none
    ***
    *** @throws       Class_name  Description_of_exception_thrown__Delete_if_none
    **************************************************************************/

    private void stopThread()
    {
      logger.info("Stopping the " + Listener_Thread.getName() + " Thread");
      Continue_Thread = false;
    }
    /*-----------------------------------------------------------------------*/

    public synchronized OpenSmMonitorService getOMS()
    {
      return osmService;
    }
     

    public void destroy()
    {
      logger.info("Destroying the " + Listener_Thread.getName() + " Thread");
      stopThread();
    }
    
   /**************************************************************************
  *** Method Name:
  ***     getInstance
  **/
  /**
  *** Get the singleton OMS_FileBasedService. This can be used if the application wants
  *** to share one manager across the whole JVM.  Currently I am not sure
  *** how this ought to be used.
  *** <p>
  ***
  *** @return       the GLOBAL (or shared) OMS_FileBasedService
  **************************************************************************/

  public static OMS_FileBasedService getInstance()
  {
    synchronized( OMS_FileBasedService.semaphore )
    {
      if ( globalUpdateService == null )
      {
        globalUpdateService = new OMS_FileBasedService( );
      }
      return globalUpdateService;
    }
  }
  /*-----------------------------------------------------------------------*/

  public Object clone() throws CloneNotSupportedException
  {
    throw new CloneNotSupportedException(); 
  }

  
  @Override
  public void run()
  {
    // the history should be in memory
    int num = omsHistory.getSize();
    int ndex = 0;
    osmService = omsHistory.getOMS(ndex++);
    logger.severe("updating listeners now");
    long updatePeriod = osmService.getFabric().getPerfMgrSweepSecs();
    updatePeriod = updatePeriod/getUpdateMultiplier();
    updatePeriod = updatePeriod > 1L ? updatePeriod: 2L;
    logger.severe("Perf Sweep Secs    : " + osmService.getFabric().getPerfMgrSweepSecs());
    logger.severe("Multiplier is      : " + getUpdateMultiplier());
    logger.severe("Update period  secs: " + updatePeriod);
    
    Thread_Running = true;
    
    
    
    // check the Thread Termination Flag, and continue if Okay
    while(Continue_Thread)
    {
      try
      {
        updateAllListeners();
        TimeUnit.SECONDS.sleep(updatePeriod);
       }
      catch (Exception e)
      {
        // nothing to do yet
        logger.severe("Crap, couldn't get a new service instance");
      }
      // advance to the next instance of the collection
      osmService = omsHistory.getOMS(ndex++);
      if(ndex >= omsHistory.getSize())
      {
        if(isWrapData())
          ndex = 0;
        else
          ndex = omsHistory.getSize() -1;
      }
    }
      
    logger.info("Terminating the " + Listener_Thread.getName() + " Thread");
    
    /* fall through, must be done! */
    Thread_Running = false;
  }
  
  /**************************************************************************
   *** Method Name:
   ***     updateAllListeners
   ***
   **/
   /**
   *** Notifies all listeners that some event has occurred.
   *** <p>
   ***
   **************************************************************************/
   private synchronized void updateAllListeners() throws Exception
   {
     
     for(OSM_ServiceChangeListener listener: Service_Listeners)
     {
       if(listener != null)
        listener.osmServiceUpdate(this, osmService);
     }
   }
   /*-----------------------------------------------------------------------*/


   public synchronized int getNumListeners()
   {
     return Service_Listeners.size();
   }
    
  /************************************************************
   * Method Name:
   *  addListener
  **/
  /**
   * Add the provided listener to the list of subscribers interested
   * in being notified of time changes.  
   *
   * @param listener
   ***********************************************************/
  
  @Override
  public synchronized void addListener(OSM_ServiceChangeListener listener)
  {
    // add the listener, and its set of events
    if(listener != null)
    {
      Service_Listeners.add(listener);
      
      // immediately give this listener a service, so it doesn't have to wait
      // for the next update period, which can be considerable.
      try
      {
        if(osmService != null)
          listener.osmServiceUpdate(this, osmService);
      }
      catch (Exception e)
      {
        // TODO Auto-generated catch bloc
        logger.severe("could not provide service update");
      }
      
    }
  }

  /************************************************************
   * Method Name:
   *  removeListener
  **/
  /**
   * Describe the method here
   *
   * @see gov.llnl.lc.infiniband.opensm.plugin.event.OsmEventUpdater#removeListener(gov.llnl.lc.infiniband.opensm.plugin.event.OsmEventListener)
  
   * @param   describe the parameters
   *
   * @return  describe the value returned
   * @param listener
   * @return
   ***********************************************************/
  
  @Override
  public synchronized boolean removeListener(OSM_ServiceChangeListener listener)
  {
    if (Service_Listeners.remove(listener))
    {
     }
    return true;
  }

  public synchronized long getUpdateMultiplier()
  {
    String sVal = serviceProps.getProperty(SmtProperty.SMT_UPDATE_MULTIPLIER.name());
    if(sVal == null)
      return 300L;
    Long lVal = new Long(sVal);
    return lVal.longValue();
  }

  public synchronized String getCommand()
  {
    return serviceProps.getProperty(SmtProperty.SMT_COMMAND.name());
  }
  
  public synchronized String getToolName()
  {
    return serviceProps.getProperty(SmtProperty.SMT_COMMAND.name());
  }
  
  public synchronized long getUpdatePeriod()
  {
    // this will have a minimum value of 2
    //
    // for off-line, this value is the fabrics update period
    // divided by the multiplier
    if(osmService != null)
    {
      int period = osmService.getFabric().getPerfMgrSweepSecs();
      long m = getUpdateMultiplier();
      long val = period/m;
      return val > 1L ? val: 2L;
    }
    return 1L;
  }

  public synchronized void setUpdatePeriod(long updatePeriod)
  {
//    OMS_FileBasedService.updatePeriod = updatePeriod;
  }

  public synchronized String getHost()
  {
    return serviceProps.getProperty(SmtProperty.SMT_HOST.name());
  }
  
  public synchronized String getPort()
  {
    return serviceProps.getProperty(SmtProperty.SMT_PORT.name());
  }
  
  public synchronized String getFile()
  {
    return serviceProps.getProperty(SmtProperty.SMT_OMS_FILE.name());
  }
  public boolean isWrapData()
  {
    String sVal = serviceProps.getProperty(SmtProperty.SMT_WRAP_DATA.name());
    if(sVal == null)
      return false;
    Boolean bVal = new Boolean(sVal);
    return bVal.booleanValue();
  }

  
  
  public synchronized void setHost(String host)
  {
    OMS_FileBasedService.host = host;
  }

//  public synchronized String getPort()
//  {
//    return port;
//  }

  public synchronized void setPort(String port)
  {
    OMS_FileBasedService.port = port;
  }

  /************************************************************
   * Method Name:
   *  main
   **/
  /**
   * Describe the method here
   *
   * @see     describe related java objects
   *
   * @param args
   ***********************************************************/
  public static void main(String[] args)
  {
    // TODO Auto-generated method stub

  }

  public void setReuseConnection(boolean reuse)
  {
    reuseConnection = reuse;
    
  }

  @Override
  public boolean isConnectionReused()
  {
    String sVal = serviceProps.getProperty(SmtProperty.SMT_REUSE.name());
    if(sVal == null)
      return false;
    Boolean bVal = new Boolean(sVal);
    return bVal.booleanValue();
  }

  @Override
  public TimeStamp getUpTime()
  {
    return UpTime;
  }

  @Override
  public synchronized boolean init(Properties properties)
  {
    if(properties != null)
    {
      // look for properties I may care about, and initialize myself
      serviceProps = properties;
      
      // read the file, and start the listener thread if not already running
      if(!Thread_Running)
        startThread();
      
      // TODO:  Move the file reading code
     }
    return false;
  }

  @Override
  public synchronized Properties getProperties()
  {
    return serviceProps;
  }

  @Override
  public boolean isReady()
  {
    return Thread_Running;
  }
  
  @Override
  public boolean waitUntilReady() throws InterruptedException
  {
    for(int count = 0; (!isReady() && count < 50); count++)
      TimeUnit.MILLISECONDS.sleep(100);

    return isReady();
  }
  @Override
  public String getName()
  {
    return this.getClass().getSimpleName();
  }

  @Override
  public OMS_List getOMS_List()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public OSM_Configuration getOsmConfig()
  {
    // TODO Auto-generated method stub
    return null;
  }

}
