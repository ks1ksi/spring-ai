/*
* Copyright 2024 - 2024 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springframework.ai.ollama.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.ollama.api.OllamaApi.DeleteModelRequest;
import org.springframework.ai.ollama.api.OllamaApi.ListModelResponse;
import org.springframework.ai.ollama.api.OllamaApi.PullModelRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;

/**
 * Helper class that allow to check if a model is available locally and pull it if not.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class OllamaModelPuller {

	private final Logger logger = LoggerFactory.getLogger(OllamaModelPuller.class);

	private OllamaApi ollamaApi;

	private final long pullRetryTimeoutMs;

	public OllamaModelPuller(OllamaApi ollamaApi) {
		this(ollamaApi, 5000);
	}

	public OllamaModelPuller(OllamaApi ollamaApi, long retryTimeoutMs) {
		this.ollamaApi = ollamaApi;
		this.pullRetryTimeoutMs = retryTimeoutMs;
	}

	public boolean isModelAvailable(String modelName) {
		ListModelResponse modelsResponse = ollamaApi.listModels();
		if (!CollectionUtils.isEmpty(modelsResponse.models())) {
			return modelsResponse.models().stream().anyMatch(m -> m.name().equals(modelName));
		}
		return false;
	}

	public boolean deleteModel(String modelName) {
		logger.info("Delete model: {}", modelName);
		if (!isModelAvailable(modelName)) {
			logger.info("Model: {} not found!", modelName);
			return false;
		}
		return this.ollamaApi.deleteModel(new DeleteModelRequest(modelName)).getStatusCode().equals(HttpStatus.OK);
	}

	public String pullModel(String modelName, boolean enablePullRetry) {
		String status = "";
		do {
			logger.info("Start Pulling model: {}", modelName);
			var progress = this.ollamaApi.pullModel(new PullModelRequest(modelName));
			status = progress.status();
			logger.info("Pulling model: {} - Status: {}", modelName, status);

			try {
				Thread.sleep(this.pullRetryTimeoutMs);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		while (enablePullRetry && !status.equals("success"));

		return status;
	}

}
