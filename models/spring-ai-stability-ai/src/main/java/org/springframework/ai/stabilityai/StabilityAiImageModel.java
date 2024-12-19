/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.stabilityai;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.ai.stabilityai.api.StabilityAiImageOptions;
import org.springframework.util.Assert;

/**
 * StabilityAiImageModel is a class that implements the ImageModel interface. It provides
 * a client for calling the StabilityAI image generation API.
 */
public class StabilityAiImageModel implements ImageModel {

	private final StabilityAiImageOptions defaultOptions;

	private final StabilityAiApi stabilityAiApi;

	public StabilityAiImageModel(StabilityAiApi stabilityAiApi) {
		this(stabilityAiApi, StabilityAiImageOptions.builder().build());
	}

	public StabilityAiImageModel(StabilityAiApi stabilityAiApi, StabilityAiImageOptions defaultOptions) {
		Assert.notNull(stabilityAiApi, "StabilityAiApi must not be null");
		Assert.notNull(defaultOptions, "StabilityAiImageOptions must not be null");
		this.stabilityAiApi = stabilityAiApi;
		this.defaultOptions = defaultOptions;
	}

	private static StabilityAiApi.GenerateImageRequest getGenerateImageRequest(ImagePrompt stabilityAiImagePrompt,
			StabilityAiImageOptions optionsToUse) {
		return new StabilityAiApi.GenerateImageRequest.Builder()
			.textPrompts(stabilityAiImagePrompt.getInstructions()
				.stream()
				.map(message -> new StabilityAiApi.GenerateImageRequest.TextPrompts(message.getText(),
						message.getWeight()))
				.collect(Collectors.toList()))
			.height(optionsToUse.getHeight())
			.width(optionsToUse.getWidth())
			.cfgScale(optionsToUse.getCfgScale())
			.clipGuidancePreset(optionsToUse.getClipGuidancePreset())
			.sampler(optionsToUse.getSampler())
			.samples(optionsToUse.getN())
			.seed(optionsToUse.getSeed())
			.steps(optionsToUse.getSteps())
			.stylePreset(optionsToUse.getStylePreset())
			.build();
	}

	public StabilityAiImageOptions getOptions() {
		return this.defaultOptions;
	}

	/**
	 * Calls the StabilityAiImageModel with the given StabilityAiImagePrompt and returns
	 * the ImageResponse. This overloaded call method lets you pass the full set of Prompt
	 * instructions that StabilityAI supports.
	 * @param imagePrompt the StabilityAiImagePrompt containing the prompt and image model
	 * options
	 * @return the ImageResponse generated by the StabilityAiImageModel
	 */
	public ImageResponse call(ImagePrompt imagePrompt) {
		// Merge the runtime options passed via the prompt with the default options
		// configured via the constructor.
		// Runtime options overwrite StabilityAiImageModel options
		StabilityAiImageOptions requestImageOptions = mergeOptions(imagePrompt.getOptions(), this.defaultOptions);

		// Copy the org.springframework.ai.model derived ImagePrompt and ImageOptions data
		// types to the data types used in StabilityAiApi
		StabilityAiApi.GenerateImageRequest generateImageRequest = getGenerateImageRequest(imagePrompt,
				requestImageOptions);

		// Make the request
		StabilityAiApi.GenerateImageResponse generateImageResponse = this.stabilityAiApi
			.generateImage(generateImageRequest);

		// Convert to org.springframework.ai.model derived ImageResponse data type
		return convertResponse(generateImageResponse);
	}

	private ImageResponse convertResponse(StabilityAiApi.GenerateImageResponse generateImageResponse) {
		List<ImageGeneration> imageGenerationList = generateImageResponse.artifacts()
			.stream()
			.map(entry -> new ImageGeneration(new Image(null, entry.base64()),
					new StabilityAiImageGenerationMetadata(entry.finishReason(), entry.seed())))
			.toList();

		return new ImageResponse(imageGenerationList, new ImageResponseMetadata());
	}

	/**
	 * Merge runtime and default {@link ImageOptions} to compute the final options to use
	 * in the request. Protected access for testing purposes, though maybe useful for
	 * future subclassing as options change.
	 */
	StabilityAiImageOptions mergeOptions(ImageOptions runtimeOptions, StabilityAiImageOptions defaultOptions) {
		if (runtimeOptions == null) {
			return defaultOptions;
		}
		StabilityAiImageOptions.Builder builder = StabilityAiImageOptions.builder()
			// Handle portable image options
			.model(ModelOptionsUtils.mergeOption(runtimeOptions.getModel(), defaultOptions.getModel()))
			.N(ModelOptionsUtils.mergeOption(runtimeOptions.getN(), defaultOptions.getN()))
			.responseFormat(ModelOptionsUtils.mergeOption(runtimeOptions.getResponseFormat(),
					defaultOptions.getResponseFormat()))
			.width(ModelOptionsUtils.mergeOption(runtimeOptions.getWidth(), defaultOptions.getWidth()))
			.height(ModelOptionsUtils.mergeOption(runtimeOptions.getHeight(), defaultOptions.getHeight()))
			.stylePreset(ModelOptionsUtils.mergeOption(runtimeOptions.getStyle(), defaultOptions.getStyle()))
			// Always set the stability-specific defaults
			.cfgScale(defaultOptions.getCfgScale())
			.clipGuidancePreset(defaultOptions.getClipGuidancePreset())
			.sampler(defaultOptions.getSampler())
			.seed(defaultOptions.getSeed())
			.steps(defaultOptions.getSteps())
			.stylePreset(defaultOptions.getStylePreset());
		if (runtimeOptions instanceof StabilityAiImageOptions) {
			StabilityAiImageOptions stabilityOptions = (StabilityAiImageOptions) runtimeOptions;
			// Handle Stability AI specific image options
			builder
				.cfgScale(ModelOptionsUtils.mergeOption(stabilityOptions.getCfgScale(), defaultOptions.getCfgScale()))
				.clipGuidancePreset(ModelOptionsUtils.mergeOption(stabilityOptions.getClipGuidancePreset(),
						defaultOptions.getClipGuidancePreset()))
				.sampler(ModelOptionsUtils.mergeOption(stabilityOptions.getSampler(), defaultOptions.getSampler()))
				.seed(ModelOptionsUtils.mergeOption(stabilityOptions.getSeed(), defaultOptions.getSeed()))
				.steps(ModelOptionsUtils.mergeOption(stabilityOptions.getSteps(), defaultOptions.getSteps()))
				.stylePreset(ModelOptionsUtils.mergeOption(stabilityOptions.getStylePreset(),
						defaultOptions.getStylePreset()));
		}

		return builder.build();
	}

}
