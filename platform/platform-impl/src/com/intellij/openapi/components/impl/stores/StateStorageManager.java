/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.*;
import com.intellij.openapi.util.Couple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public interface StateStorageManager {
  void addMacro(@NotNull String macro, @NotNull String expansion);

  @Nullable
  TrackingPathMacroSubstitutor getMacroSubstitutor();

  @Nullable
  StateStorage getStateStorage(@NotNull Storage storageSpec);

  @Nullable
  StateStorage getStateStorage(@NotNull String fileSpec, @NotNull RoamingType roamingType);

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  @Nullable
  /**
   * @deprecated Use {@link #getStateStorage(String, com.intellij.openapi.components.RoamingType)}
   * to remove in IDEA 15
    */
  StateStorage getFileStateStorage(@NotNull String fileSpec);

  @NotNull
  Couple<Collection<FileBasedStorage>> getCachedFileStateStorages(@NotNull Collection<String> changed, @NotNull Collection<String> deleted);

  @NotNull
  Collection<String> getStorageFileNames();

  void clearStateStorage(@NotNull String file);

  @Nullable
  ExternalizationSession startExternalization();

  @Nullable
  StateStorage getOldStorage(@NotNull Object component, @NotNull String componentName, @NotNull StateStorageOperation operation);

  @NotNull
  String expandMacros(@NotNull String file);

  @NotNull
  String collapseMacros(@NotNull String path);

  void setStreamProvider(@Nullable StreamProvider streamProvider);

  @Nullable
  StreamProvider getStreamProvider();

  interface ExternalizationSession {
    void setState(@NotNull Storage[] storageSpecs, @NotNull Object component, @NotNull String componentName, @NotNull Object state);

    void setStateInOldStorage(@NotNull Object component, @NotNull String componentName, @NotNull Object state);

    /**
     * return empty list if nothing to save
     */
    @NotNull
    List<StateStorage.SaveSession> createSaveSessions();
  }
}