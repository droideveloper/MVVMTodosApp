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
package org.fs.mvvm.todo.managers;

import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.List;
import java8.util.function.Predicate;
import org.fs.mvvm.todo.entities.Entry;

public interface IDatabaseManager {

  Observable<List<Entry>> all();

  Single<List<Entry>> all(Predicate<Entry> filter);

  Single<Entry> firstOrDefault(Predicate<Entry> filter);

  Observable<Boolean> create(Entry entry);

  Observable<Boolean> update(Entry entry);

  Observable<Boolean> delete(Entry entry);
}
