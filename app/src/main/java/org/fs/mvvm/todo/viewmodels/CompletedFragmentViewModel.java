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
package org.fs.mvvm.todo.viewmodels;

import android.databinding.Bindable;
import android.databinding.ObservableArrayList;
import android.databinding.ObservableList;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import org.fs.mvvm.data.AbstractViewModel;
import org.fs.mvvm.data.UsecaseType;
import org.fs.mvvm.core.BusManager;
import org.fs.mvvm.todo.BR;
import org.fs.mvvm.todo.BuildConfig;
import org.fs.mvvm.todo.R;
import org.fs.mvvm.todo.common.DaggerViewModelComponent;
import org.fs.mvvm.todo.common.ViewModelModule;
import org.fs.mvvm.todo.entities.Category;
import org.fs.mvvm.todo.entities.Entry;
import org.fs.mvvm.todo.events.DeletedEventType;
import org.fs.mvvm.todo.events.RecoveredEventType;
import org.fs.mvvm.todo.events.StateChangeEventType;
import org.fs.mvvm.todo.managers.IDatabaseManager;
import org.fs.mvvm.todo.utils.SwipeDeleteCallback;
import org.fs.mvvm.todo.views.CompletedFragmentViewType;
import org.fs.mvvm.todo.views.adapters.EntryRecyclerAdapter;
import org.fs.mvvm.utils.Objects;

public final class CompletedFragmentViewModel extends AbstractViewModel<CompletedFragmentViewType> {

  public final static String KEY_CATEGORY = "entry.category";

  private Disposable disposable;
  private Category category;
  ObservableList<Entry> dataSource = new ObservableArrayList<>();

  @Inject RecyclerView.LayoutManager layoutManager;
  @Inject RecyclerView.ItemAnimator  itemAnimator;
  @Inject EntryRecyclerAdapter itemSource;
  @Inject ItemTouchHelper touchHelper;

  @Inject UsecaseType<List<Entry>> usecase;
  @Inject IDatabaseManager dbManager;

  public CompletedFragmentViewModel(CompletedFragmentViewType view) {
    super(view);
  }

  @Override public void restoreState(Bundle restoreState) {
    if (restoreState != null) {
      if (restoreState.containsKey(KEY_CATEGORY)) {
        category = restoreState.getParcelable(KEY_CATEGORY);
      }
    }
  }

  @Override public void storeState(Bundle storeState) {
    if (category != null) {
      storeState.putParcelable(KEY_CATEGORY, category);
    }
  }

  @Override public void onCreate() {
    DaggerViewModelComponent.builder()
        .viewModelModule(new ViewModelModule(view.getContext(), dataSource, getSwipeListener()))
        .build()
        .inject(this);
  }

  @Override public void onStart() {
    if (view.isAvailable()) {
      disposable = BusManager.add((event) -> {
        //register if we have state change in entry like ACTIVE to COMPLETED or COMPLETED to ACTIVE
        if (event instanceof StateChangeEventType) {
          StateChangeEventType stateEvent = Objects.toObject(event);
          if (stateEvent.isCompleted()) {
            if (!dataSource.contains(stateEvent.toEntry())) {
              dataSource.add(stateEvent.toEntry());
            }
          } else if (stateEvent.isActive()) {
            if (dataSource.contains(stateEvent.toEntry())) {
              dataSource.remove(stateEvent.toEntry());
            }
          }
        } else if (event instanceof RecoveredEventType) {
          RecoveredEventType recoverEvent = Objects.toObject(event);
          if (!dataSource.contains(recoverEvent.toEntry()) && isCompleted(recoverEvent.toEntry())) {
            dataSource.add(recoverEvent.toEntry());
          }
        } else if (event instanceof DeletedEventType) {
          DeletedEventType deleteEvent = Objects.toObject(event);
          if (dataSource.contains(deleteEvent.toEntry())) {
            dataSource.remove(deleteEvent.toEntry());
          }
        }
      });
      usecase.async()
        .flatMap(Observable::fromIterable)
        .filter(x -> x.getTodoState() == Entry.COMPLETED)
        .toList()
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(items -> {
          if (!Objects.isNullOrEmpty(dataSource)) {
            dataSource.clear();
          }
          if (!Objects.isNullOrEmpty(items)) {
            dataSource.addAll(items);
          }
        }, error -> {
          if (view.isAvailable()) {
            String errorStr = view.getStringResource(R.string.addError);
            view.showError(errorStr);
            log(error);
          }
        });
    }
  }

  @Override public void onStop() {
    if(disposable != null) {
      BusManager.remove(disposable);
      disposable = null;
    }
  }

  private SwipeDeleteCallback.OnSwipedListener getSwipeListener() {
    return (viewHolder, swipeDirection) -> {
      if (view.isAvailable()) {
        //delete item
        final int position = viewHolder.getAdapterPosition();
        Entry deleted = dataSource.remove(position);
        dbManager.delete(deleted)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(x -> {
              //change its id.
              deleted.setTodoId(Integer.MIN_VALUE);
              log(Log.INFO,
                  String.format(Locale.ENGLISH, "%s is activated. %s",
                      deleted.getTodoName(), String.valueOf(x))
              );
            });
        BusManager.send(new DeletedEventType(deleted));
        //create message to notify user
        String ok = view.getStringResource(android.R.string.ok);
        String msg = view.getStringResource(R.string.recoverDeleteItem);
        msg = String.format(Locale.getDefault(), msg, ok, deleted.getTodoName());
        //add deleted back if user recover click
        view.showError(msg, ok, v -> {
          if (Objects.isNullOrEmpty(dataSource)) {
            //the idea is we do not want error if collection is empty
            dataSource.add(deleted);
          } else {
            dataSource.add(position, deleted);
          }
          dbManager.create(deleted)
              .subscribeOn(Schedulers.io())
              .observeOn(AndroidSchedulers.mainThread())
              .subscribe(x -> {
                BusManager.send(new RecoveredEventType(deleted));
                log(Log.ERROR,
                    String.format(Locale.ENGLISH, "%s previously deleted inserted.",
                        deleted.getTodoName())
                );
              });
        });
      }
    };
  }

  @Bindable public EntryRecyclerAdapter getItemSource() {
    return this.itemSource;
  }

  public void setItemSource(EntryRecyclerAdapter itemSource) {
    this.itemSource = itemSource;
    notifyPropertyChanged(BR.itemSource);
  }

  @Bindable public RecyclerView.ItemAnimator getItemAnimator() {
    return this.itemAnimator;
  }

  public void setItemAnimator(RecyclerView.ItemAnimator itemAnimator) {
    this.itemAnimator = itemAnimator;
    notifyPropertyChanged(BR.itemAnimator);
  }

  @Bindable public ItemTouchHelper getTouchHelper() {
    return this.touchHelper;
  }

  public void setTouchHelper(ItemTouchHelper touchHelper) {
    this.touchHelper = touchHelper;
    notifyPropertyChanged(BR.touchHelper);
  }

  @Bindable public RecyclerView.LayoutManager getLayoutManager() {
    return this.layoutManager;
  }

  public void setLayoutManager(RecyclerView.LayoutManager layoutManager) {
    this.layoutManager = layoutManager;
    notifyPropertyChanged(BR.layoutManager);
  }

  @Override protected boolean isLogEnabled() {
    return BuildConfig.DEBUG;
  }

  @Override protected String getClassTag() {
    return CompletedFragmentViewModel.class.getSimpleName();
  }

  private boolean isCompleted(Entry entry) {
    return entry.getTodoState() == Entry.COMPLETED;
  }
}