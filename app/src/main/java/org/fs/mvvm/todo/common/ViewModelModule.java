/*
 * todos Copyright (C) 2016 Fatih.
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
package org.fs.mvvm.todo.common;

import android.content.Context;
import android.databinding.ObservableList;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.helper.ItemTouchHelper;
import dagger.Module;
import dagger.Provides;
import java.util.List;
import org.fs.mvvm.data.IUsecase;
import org.fs.mvvm.injections.ForFragment;
import org.fs.mvvm.todo.entities.Category;
import org.fs.mvvm.todo.entities.Entry;
import org.fs.mvvm.todo.managers.DatabaseManager;
import org.fs.mvvm.todo.managers.IDatabaseManager;
import org.fs.mvvm.todo.usecases.EntryActiveUsecase;
import org.fs.mvvm.todo.usecases.EntryAllUsecase;
import org.fs.mvvm.todo.usecases.EntryCompletedUsecase;
import org.fs.mvvm.todo.utils.SwipeDeleteCallback;
import org.fs.mvvm.todo.views.adapters.EntryRecyclerAdapter;
import org.fs.mvvm.widget.RecyclerView;

@Module
public class ViewModelModule {

  private final Context context;
  private final int categoryId;
  private final ObservableList<Entry> dataSource;
  private final SwipeDeleteCallback.OnSwipedListener listener;

  public ViewModelModule(Context context, int categoryId, ObservableList<Entry> dataSource, SwipeDeleteCallback.OnSwipedListener listener) {
    this.context = context;
    this.categoryId = categoryId;
    this.dataSource = dataSource;
    this.listener = listener;
  }

  @Provides @ForFragment IDatabaseManager provideDatabaseManager() {
    return new DatabaseManager(context);
  }

  @Provides @ForFragment IUsecase<List<Entry>> provideUsecase(IDatabaseManager dbManager) {
    if (categoryId == Category.ALL) {
      return new EntryAllUsecase.Builder()
          .dbManager(dbManager)
          .build();
    } else if (categoryId == Category.ACTIVE) {
      return new EntryActiveUsecase.Builder()
          .dbManager(dbManager)
          .build();
    } else {
      return new EntryCompletedUsecase.Builder()
          .dbManager(dbManager)
          .build();
    }
  }

  @Provides @ForFragment EntryRecyclerAdapter provideRecyclerAdapter() {
    return EntryRecyclerAdapter.createSingleMode(context, dataSource);
  }

  @Provides @ForFragment RecyclerView.LayoutManager provideLayoutManager() {
    return new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
  }

  @Provides @ForFragment RecyclerView.ItemAnimator provideDefaultAnimator() {
    return new DefaultItemAnimator();
  }

  @Provides @ForFragment ItemTouchHelper provideTouchHelper() {
    return new ItemTouchHelper(SwipeDeleteCallback.create(listener));
  }
}