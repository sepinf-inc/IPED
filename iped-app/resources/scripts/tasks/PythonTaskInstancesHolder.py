# -*- coding: utf-8 -*-
"""
Script Name: PythonTaskInstancesHolder.py
Author: Dalpian
Date: August 30, 2025
Version: 1.0

Description:
    This script is not an executable IPED task. It functions as a 
    helper module for the `PythonTask.java` Java class.
    
    Its purpose is to manage and store instances of task scripts
    in a global variable (dictionary), ensuring that each worker
    uses a single instance of each task during processing, solving
    a problem with JEP and multithreading.
"""
import importlib

# Dictionary to save workers instances per script
INSTANCES_PER_WORKER = {}

'''
Main class
'''
class PythonTaskInstancesHolder:
       
    def __init__(self):
        pass
      
    def callFunction(self, worker_id, script_name, functionName, *args):

        instance = self.getInstance(worker_id, script_name)
        
        # Check if the instance was created successfully before proceeding
        if instance is None:
            self.logger.error(f"ERROR: Cannot call function '{functionName}' because instance of '{script_name}' could not be created.")
            return None

        function_to_call = getattr(instance, functionName)

        return function_to_call(*args)


    def getInstance(self, worker_id, script_name):
        '''
        Gets or creates an instance, assuming module_name and class_name are the same.
        '''
        # The script name will be used as the dictionary key
        script_key = script_name
        
        if worker_id not in INSTANCES_PER_WORKER:
            INSTANCES_PER_WORKER[worker_id] = {}
            
        if script_key not in INSTANCES_PER_WORKER[worker_id]:
            self.logger.debug(f"Instance of '{script_key}' not found for worker {worker_id}. Creating a new one...")
            
            try:             
                module = importlib.import_module(script_name)
                class_to_instantiate = getattr(module, script_name)
                INSTANCES_PER_WORKER[worker_id][script_key] = class_to_instantiate()

            except ImportError:
                self.logger.error(f"ERROR: Module '{script_name}' could not be found. Check BASE_MODULE_PATH and the file name.")
                raise
            except AttributeError:
                self.logger.error(f"ERROR: Class '{script_name}' not found inside module '{script_name}'.")
                raise
            except Exception as e:
                self.logger.error(f"An unexpected error occurred while instantiating '{script_name}': {e}")
                raise
            
        return INSTANCES_PER_WORKER[worker_id][script_key]