/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;

import javax.swing.*;

class RerunAction extends AnAction {
  private final InspectionResultsView myView;

  RerunAction(InspectionResultsView view, JComponent comp) {
    super(InspectionsBundle.message("inspection.action.rerun"), InspectionsBundle.message("inspection.action.rerun"),
          AllIcons.Actions.Rerun);
    myView = view;
    registerCustomShortcutSet(CommonShortcuts.getRerun(), comp);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(myView.isRerunAvailable());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    rerun();
  }

  private void rerun() {
    myView.rerun();
  }
}
