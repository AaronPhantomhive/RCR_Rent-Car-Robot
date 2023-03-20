/*
 * Copyright 2018 IBM Corp. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package application;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.watson.developer_cloud.assistant.v1.Assistant;
import com.ibm.watson.developer_cloud.assistant.v1.model.Context;
import com.ibm.watson.developer_cloud.assistant.v1.model.CreateCounterexample;
import com.ibm.watson.developer_cloud.assistant.v1.model.CreateDialogNode;
import com.ibm.watson.developer_cloud.assistant.v1.model.CreateEntity;
import com.ibm.watson.developer_cloud.assistant.v1.model.CreateIntent;
import com.ibm.watson.developer_cloud.assistant.v1.model.CreateWorkspaceOptions;
import com.ibm.watson.developer_cloud.assistant.v1.model.InputData;
import com.ibm.watson.developer_cloud.assistant.v1.model.MessageOptions;
import com.ibm.watson.developer_cloud.assistant.v1.model.MessageResponse;
import com.ibm.watson.developer_cloud.assistant.v1.model.Workspace;
import com.ibm.watson.developer_cloud.service.security.IamOptions;

import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;

@RestController
public class Controller {
  private static final Logger LOGGER = LoggerFactory.getLogger(Controller.class);

  private static final String ASSISTANT_VERSION_DATE = "2018-07-10";
  private static final String ASSISTANT_URL = "watson_conversation_url";
  private static final String ASSISTANT_USERNAME = "watson_conversation_username";
  private static final String ASSISTANT_PASSWORD = "watson_conversation_password";
  private static final String ASSISTANT_IAM_APIKEY = "watson_conversation_apikey";

  private static final String PROP_METADATA = "metadata";
  private static final String PROP_COUNTEREXAMPLES = "counterexamples";
  private static final String PROP_DIALOG_NODES = "dialog_nodes";
  private static final String PROP_ENTITIES = "entities";
  private static final String PROP_INTENTS = "intents";
  private static final String PROP_LANGUAGE = "language";
  private static final String PROP_DESCRIPTION = "description";
  private static final String PROP_NAME = "name";

  private static final String TRAINING_FILE = "/training/bank_simple_workspace.json";
  private static final String WORKSPACE_ID = "WORKSPACE_ID";

  private Assistant service;
  private String workspaceId;

  @Autowired
  Environment env;

  @RequestMapping(value = "/api/message", method = RequestMethod.POST)
  public MessageResponse message(@RequestBody MessageOptionsWrapper messageOptions) {
    Assistant service = getAssistantService();
    if (messageOptions != null) {
      String text = (String) messageOptions.getText();
      InputData inputData = new InputData.Builder(text).build();
      Context context = messageOptions.getContext();
      MessageOptions options = new MessageOptions.Builder(workspaceId)
          .input(inputData)
          .context(context)
          .build();
      return service.message(options).execute();
    } else {
      LOGGER.warn("Call to /api/message with an empty body");
    }

    return null;
  }

  /**
   * Gets the assistant service from the environment variables or
   * localdev-config.json file.
   *
   * @return the assistant service
   */
  private Assistant getAssistantService() {
    if (service == null || workspaceId == null) {
      String url = env.getProperty(ASSISTANT_URL);
      String username = env.getProperty(ASSISTANT_USERNAME);
      String password = env.getProperty(ASSISTANT_PASSWORD);
      String iamApikey = env.getProperty(ASSISTANT_IAM_APIKEY);
      if (iamApikey != null) {
        IamOptions iamOptions = new IamOptions.Builder()
            .apiKey(iamApikey)
            .build();
        service = new Assistant(ASSISTANT_VERSION_DATE, iamOptions);
      } else {
        service = new Assistant(ASSISTANT_VERSION_DATE, username, password);
      }
      service.setEndPoint(url);
      workspaceId = getOrCreateWorkspace();
    }
    return service;
  }

  /**
   * Gets the or create a new workspace id
   *
   * @return A workspace id
   */
  private String getOrCreateWorkspace() {
    List<Workspace> workspaces = null;
    if (env.getProperty(WORKSPACE_ID) != null) {
      return env.getProperty(WORKSPACE_ID);
    }
    try {
      workspaces = service.listWorkspaces().execute().getWorkspaces();
    } catch (RuntimeException e) {
      LOGGER.error("Error getting the workspaces", e);
      throw e;
    }

    if (!workspaces.isEmpty()) {
      // Return the first workspace among your workspaces
      return workspaces.get(0).getWorkspaceId();
    } else {
      LOGGER.info("Creating a new workspace...");
      CreateWorkspaceOptions options = parseWorkspaceFromTrainingFile();
      // Create new workspace with training file
      Workspace newWorkspace = service.createWorkspace(options).execute();
      LOGGER.info("Workspace created. id: " + newWorkspace.getWorkspaceId());

      return newWorkspace.getWorkspaceId();
    }
  }

  /**
   * returns a CreateWorkspaceOptions from a training file.
   *
   * @param trainingFile the training file
   * @return A {@link CreateWorkspaceOptions}
   */
  @SuppressWarnings("unchecked")
  private CreateWorkspaceOptions parseWorkspaceFromTrainingFile() {
    JSONParser parser = new JSONParser(JSONParser.MODE_PERMISSIVE);
    try {
      InputStream stream = Controller.class.getResourceAsStream(TRAINING_FILE);
      JSONObject jsonObject = (JSONObject) parser.parse(stream);
      String name = (String) jsonObject.get(PROP_NAME);
      String description = (String) jsonObject.get(PROP_DESCRIPTION);
      String language = (String) jsonObject.get(PROP_LANGUAGE);
      List<CreateIntent> intents = (List<CreateIntent>) jsonObject.get(PROP_INTENTS);
      List<CreateEntity> entities = (List<CreateEntity>) jsonObject.get(PROP_ENTITIES);
      List<CreateDialogNode> dialogNodes = (List<CreateDialogNode>) jsonObject.get(PROP_DIALOG_NODES);
      List<CreateCounterexample> counterexamples = (List<CreateCounterexample>) jsonObject.get(PROP_COUNTEREXAMPLES);
      Map<String, Object> metadata = (Map<String, Object>) jsonObject.get(PROP_METADATA);

      CreateWorkspaceOptions options = new CreateWorkspaceOptions.Builder()
          .name(name)
          .description(description)
          .language(language)
          .intents(intents)
          .entities(entities)
          .dialogNodes(dialogNodes)
          .counterexamples(counterexamples)
          .metadata(metadata)
          .build();

      return options;
    } catch (Exception e) {
      throw new RuntimeException("Error parsing the training file.", e);
    }
  }
}
