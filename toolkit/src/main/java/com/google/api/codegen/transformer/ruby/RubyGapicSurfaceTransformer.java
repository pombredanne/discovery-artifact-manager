/* Copyright 2017 Google Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.api.codegen.transformer.ruby;

import com.google.api.codegen.GeneratorVersionProvider;
import com.google.api.codegen.InterfaceView;
import com.google.api.codegen.TargetLanguage;
import com.google.api.codegen.config.GapicProductConfig;
import com.google.api.codegen.config.PackageMetadataConfig;
import com.google.api.codegen.config.ProductServiceConfig;
import com.google.api.codegen.gapic.GapicCodePathMapper;
import com.google.api.codegen.transformer.BatchingTransformer;
import com.google.api.codegen.transformer.DynamicLangApiMethodTransformer;
import com.google.api.codegen.transformer.FeatureConfig;
import com.google.api.codegen.transformer.FileHeaderTransformer;
import com.google.api.codegen.transformer.GapicInterfaceContext;
import com.google.api.codegen.transformer.GrpcStubTransformer;
import com.google.api.codegen.transformer.ModelToViewTransformer;
import com.google.api.codegen.transformer.ModelTypeTable;
import com.google.api.codegen.transformer.PageStreamingTransformer;
import com.google.api.codegen.transformer.PathTemplateTransformer;
import com.google.api.codegen.transformer.ServiceTransformer;
import com.google.api.codegen.transformer.SurfaceNamer;
import com.google.api.codegen.util.ruby.RubyTypeTable;
import com.google.api.codegen.viewmodel.ApiMethodView;
import com.google.api.codegen.viewmodel.DynamicLangXApiView;
import com.google.api.codegen.viewmodel.GrpcStreamingDetailView;
import com.google.api.codegen.viewmodel.ImportSectionView;
import com.google.api.codegen.viewmodel.LongRunningOperationDetailView;
import com.google.api.codegen.viewmodel.PathTemplateGetterFunctionView;
import com.google.api.codegen.viewmodel.ViewModel;
import com.google.api.codegen.viewmodel.metadata.VersionIndexRequireView;
import com.google.api.codegen.viewmodel.metadata.VersionIndexView;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.common.collect.ImmutableList;
import java.util.List;

/** The ModelToViewTransformer to transform a Model into the standard GAPIC surface in Ruby. */
public class RubyGapicSurfaceTransformer implements ModelToViewTransformer {
  private static final String VERSION_INDEX_TEMPLATE_FILE = "ruby/version_index.snip";
  private static final String XAPI_TEMPLATE_FILENAME = "ruby/main.snip";

  private final GapicCodePathMapper pathMapper;
  private final PackageMetadataConfig packageConfig;
  private final FileHeaderTransformer fileHeaderTransformer =
      new FileHeaderTransformer(new RubyImportSectionTransformer());
  private final DynamicLangApiMethodTransformer apiMethodTransformer =
      new DynamicLangApiMethodTransformer(new RubyApiMethodParamTransformer());
  private final ServiceTransformer serviceTransformer = new ServiceTransformer();
  private final GrpcStubTransformer grpcStubTransformer = new GrpcStubTransformer();
  private final PageStreamingTransformer pageStreamingTransformer = new PageStreamingTransformer();
  private final BatchingTransformer batchingTransformer = new BatchingTransformer();
  private final PathTemplateTransformer pathTemplateTransformer = new PathTemplateTransformer();

  public RubyGapicSurfaceTransformer(
      GapicCodePathMapper pathMapper, PackageMetadataConfig packageConfig) {
    this.pathMapper = pathMapper;
    this.packageConfig = packageConfig;
  }

  @Override
  public List<String> getTemplateFileNames() {
    return ImmutableList.of(XAPI_TEMPLATE_FILENAME, VERSION_INDEX_TEMPLATE_FILE);
  }

  @Override
  public List<ViewModel> transform(Model model, GapicProductConfig productConfig) {
    ImmutableList.Builder<ViewModel> views = ImmutableList.builder();
    views.add(generateVersionIndexView(model, productConfig));
    views.addAll(generateApiClasses(model, productConfig));
    return views.build();
  }

  private List<ViewModel> generateApiClasses(Model model, GapicProductConfig productConfig) {
    SurfaceNamer namer = new RubySurfaceNamer(productConfig.getPackageName());
    FeatureConfig featureConfig = new RubyFeatureConfig();
    ImmutableList.Builder<ViewModel> serviceSurfaces = ImmutableList.builder();
    for (Interface apiInterface : new InterfaceView().getElementIterable(model)) {
      ModelTypeTable modelTypeTable =
          new ModelTypeTable(
              new RubyTypeTable(productConfig.getPackageName()),
              new RubyModelTypeNameConverter(productConfig.getPackageName()));
      GapicInterfaceContext context =
          GapicInterfaceContext.create(
              apiInterface, productConfig, modelTypeTable, namer, featureConfig);
      serviceSurfaces.add(generateApiClass(context));
    }
    return serviceSurfaces.build();
  }

  private ViewModel generateApiClass(GapicInterfaceContext context) {
    SurfaceNamer namer = context.getNamer();
    String subPath = pathMapper.getOutputPath(context.getInterface(), context.getProductConfig());
    String name = namer.getApiWrapperClassName(context.getInterfaceConfig());
    List<ApiMethodView> methods = generateApiMethods(context);

    DynamicLangXApiView.Builder xapiClass = DynamicLangXApiView.newBuilder();
    xapiClass.templateFileName(XAPI_TEMPLATE_FILENAME);
    xapiClass.outputPath(namer.getSourceFilePath(subPath, name));

    xapiClass.fileHeader(fileHeaderTransformer.generateFileHeader(context));
    xapiClass.protoFilename(context.getInterface().getFile().getSimpleName());

    xapiClass.name(name);
    xapiClass.doc(serviceTransformer.generateServiceDoc(context, methods.get(0)));
    xapiClass.stubs(grpcStubTransformer.generateGrpcStubs(context));

    ProductServiceConfig productServiceConfig = new ProductServiceConfig();
    xapiClass.serviceAddress(
        productServiceConfig.getServiceAddress(context.getInterface().getModel()));
    xapiClass.servicePort(productServiceConfig.getServicePort());
    xapiClass.serviceTitle(productServiceConfig.getTitle(context.getInterface().getModel()));
    xapiClass.authScopes(productServiceConfig.getAuthScopes(context.getInterface().getModel()));
    xapiClass.hasDefaultServiceAddress(context.getInterfaceConfig().hasDefaultServiceAddress());
    xapiClass.hasDefaultServiceScopes(context.getInterfaceConfig().hasDefaultServiceScopes());

    xapiClass.pageStreamingDescriptors(pageStreamingTransformer.generateDescriptors(context));
    xapiClass.batchingDescriptors(batchingTransformer.generateDescriptors(context));
    xapiClass.longRunningDescriptors(ImmutableList.<LongRunningOperationDetailView>of());
    xapiClass.grpcStreamingDescriptors(ImmutableList.<GrpcStreamingDetailView>of());
    xapiClass.hasPageStreamingMethods(context.getInterfaceConfig().hasPageStreamingMethods());
    xapiClass.hasBatchingMethods(context.getInterfaceConfig().hasBatchingMethods());
    xapiClass.hasLongRunningOperations(context.getInterfaceConfig().hasLongRunningOperations());

    xapiClass.pathTemplates(pathTemplateTransformer.generatePathTemplates(context));
    xapiClass.formatResourceFunctions(
        pathTemplateTransformer.generateFormatResourceFunctions(context));
    xapiClass.parseResourceFunctions(
        pathTemplateTransformer.generateParseResourceFunctions(context));
    xapiClass.pathTemplateGetterFunctions(ImmutableList.<PathTemplateGetterFunctionView>of());

    xapiClass.methodKeys(ImmutableList.<String>of());
    xapiClass.interfaceKey(context.getInterface().getFullName());
    xapiClass.clientConfigPath(namer.getClientConfigPath(context.getInterface()));
    xapiClass.grpcClientTypeName(
        namer.getAndSaveNicknameForGrpcClientTypeName(
            context.getModelTypeTable(), context.getInterface()));

    xapiClass.apiMethods(methods);

    xapiClass.toolkitVersion(GeneratorVersionProvider.getGeneratorVersion());
    xapiClass.packageVersion(
        packageConfig.generatedPackageVersionBound(TargetLanguage.RUBY).lower());

    return xapiClass.build();
  }

  private List<ApiMethodView> generateApiMethods(GapicInterfaceContext context) {
    ImmutableList.Builder<ApiMethodView> apiMethods = ImmutableList.builder();
    for (Method method : context.getSupportedMethods()) {
      apiMethods.add(apiMethodTransformer.generateMethod(context.asDynamicMethodContext(method)));
    }
    return apiMethods.build();
  }

  private ViewModel generateVersionIndexView(Model model, GapicProductConfig productConfig) {
    SurfaceNamer namer = new RubySurfaceNamer(productConfig.getPackageName());
    ImmutableList.Builder<VersionIndexRequireView> requireViews = ImmutableList.builder();
    Iterable<Interface> interfaces = new InterfaceView().getElementIterable(model);
    for (Interface apiInterface : interfaces) {
      requireViews.add(
          VersionIndexRequireView.newBuilder()
              .clientName(namer.getNotImplementedString("VersionIndexRequireView.clientName"))
              .fileName(namer.getServiceFileName(productConfig.getInterfaceConfig(apiInterface)))
              .build());
    }

    return VersionIndexView.newBuilder()
        .apiVersion(namer.getApiWrapperModuleVersion())
        // The following assumes that all generated interfaces are generated to the same output path.
        .outputPath(pathMapper.getOutputPath(interfaces.iterator().next(), productConfig) + ".rb")
        .requireViews(requireViews.build())
        .templateFileName(VERSION_INDEX_TEMPLATE_FILE)
        .packageVersion(packageConfig.generatedPackageVersionBound(TargetLanguage.RUBY).lower())
        .fileHeader(
            fileHeaderTransformer.generateFileHeader(
                productConfig, ImportSectionView.newBuilder().build(), namer))
        .build();
  }
}
