package hara.lang.experimental;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import hara.lang.base.AbstractIterator;
import hara.lang.base.ArrayList;
import hara.lang.base.AtomicReference;
import hara.lang.base.Beta;
import hara.lang.base.CachedIterator;
import hara.lang.base.DistinctIterator;
import hara.lang.base.EmptyIterator;
import hara.lang.base.GroupedIterator;
import hara.lang.base.IteratorModule;
import hara.lang.base.Map;
import hara.lang.base.Nullable;
import hara.lang.base.Option;
import hara.lang.base.PeekingIterator;
import hara.lang.base.Seq;
import hara.lang.base.Set;
import hara.lang.base.Stream;
import hara.lang.base.T;
import hara.lang.base.Traversable;
import hara.lang.base.Tuple2;
import hara.lang.base.Tuple3;
import hara.lang.base.io;
import hara.lang.base.ConcatIterator.Iterators;
import hara.lang.base.Iter.ConcatenatedIterator;
import hara.lang.base.Iter.MergingIterator;
import hara.lang.base.Iter.UnmodifiableIterator;

public interface Iter {

	

	//
	// Filter
	//

	/**
	 * @param it an iterator
	 * @param f  a predicate
	 * @return an iterator which only yields values that satisfy the predicate
	 */
	public static <V> Iterator<V> filter(Iterator<V> it, Predicate<V> f) {
		return new Iterator<V>() {

			private Object next = NONE;
			private boolean done = false;

			private void prime() {
				if (next == NONE && !done) {
					while (it.hasNext()) {
						next = it.next();
						if (f.test((V) next)) {
							return;
						}
					}
					done = true;
				}
			}

			@Override
			public boolean hasNext() {
				prime();
				return !done;
			}

			@Override
			public V next() {
				prime();
				if (next == NONE) {
					throw new NoSuchElementException();
				}

				V val = (V) next;
				next = NONE;
				return val;
			}
		};
	}
	public static <T> boolean addAll(Collection<T> addTo, Iterator<? extends T> iterator) {
		checkNotNull(addTo);
		checkNotNull(iterator);
		boolean wasModified = false;
		while (iterator.hasNext()) {
			wasModified |= addTo.add(iterator.next());
		}
		return wasModified;
	}

	public static int frequency(Iterator<?> iterator, @Nullable Object element) {
		int count = 0;
		while (contains(iterator, element)) {
			// Since it lives in the same class, we know contains gets to the element and
			// then stops,
			// though that isn't currently publicly documented.
			count++;
		}
		return count;
	}

	public static <T> Iterator<T> cycle(final Iterable<T> iterable) {
		return new Iterator<T>() {
			Iterator<T> iterator = emptyModifiableIterator();

			@Override
			public boolean hasNext() {
				/*
				 * Don't store a new Iterator until we know the user can't remove() the last
				 * returned element anymore. Otherwise, when we remove from the old iterator, we
				 * may be invalidating the new one. The result is a
				 * ConcurrentModificationException or other bad behavior.
				 *
				 * (If we decide that we really, really hate allocating two Iterators per cycle
				 * instead of one, we can optimistically store the new Iterator and then be
				 * willing to throw it out if the user calls remove().)
				 */
				return iterator.hasNext() || iterable.iterator().hasNext();
			}

			@Override
			public T next() {
				if (!iterator.hasNext()) {
					iterator = iterable.iterator();
					if (!iterator.hasNext()) {
						throw new NoSuchElementException();
					}
				}
				return iterator.next();
			}

			@Override
			public void remove() {
				iterator.remove();
			}
		};
	}

	@SafeVarargs
	public static <T> Iterator<T> consumingForArray(final T... elements) {
		return new UnmodifiableIterator<T>() {
			int index = 0;

			@Override
			public boolean hasNext() {
				return index < elements.length;
			}

			@Override
			public T next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}
				T result = elements[index];
				elements[index] = null;
				index++;
				return result;
			}
		};
	}
	

	  public static <T> Iterator<T> concat(
	      Iterator<? extends T> a, Iterator<? extends T> b, Iterator<? extends T> c) {
	    return concat(consumingForArray(a, b, c));
	  }

	  public static <T> Iterator<T> concat(Iterator<? extends Iterator<? extends T>> inputs) {
	    return new ConcatenatedIterator<T>(inputs);
	  }
	  


	  private static <T> UnmodifiableIterator<List<T>> partitionImpl(
	      final Iterator<T> iterator, final int size, final boolean pad) {
	    checkNotNull(iterator);
	    checkArgument(size > 0);
	    return new UnmodifiableIterator<List<T>>() {
	      @Override
	      public boolean hasNext() {
	        return iterator.hasNext();
	      }

	      @Override
	      public List<T> next() {
	        if (!hasNext()) {
	          throw new NoSuchElementException();
	        }
	        Object[] array = new Object[size];
	        int count = 0;
	        for (; count < size && iterator.hasNext(); count++) {
	          array[count] = iterator.next();
	        }
	        for (int i = count; i < size; i++) {
	          array[i] = null; // for GWT
	        }

	        @SuppressWarnings("unchecked") // we only put Ts in it
	        List<T> list = Collections.unmodifiableList((List<T>) Arrays.asList(array));
	        return (pad || count == size) ? list : list.subList(0, count);
	      }
	    };
	  }

	  /**
	   * Returns a view of {@code unfiltered} containing all elements that satisfy the input predicate
	   * {@code retainIfTrue}.
	   */
	  public static <T> UnmodifiableIterator<T> filter(
	      final Iterator<T> unfiltered, final Predicate<? super T> retainIfTrue) {
	    return new AbstractIterator<T>() {
	      @Override
	      protected T computeNext() {
	        while (unfiltered.hasNext()) {
	          T element = unfiltered.next();
	          if (retainIfTrue.apply(element)) {
	            return element;
	          }
	        }
	        return endOfData();
	      }
	    };
	  }

	  

	  /**
	   * Returns {@code true} if one or more elements returned by {@code iterator} satisfy the given
	   * predicate.
	   */
	  public static <T> boolean any(Iterator<T> iterator, Predicate<? super T> predicate) {
	    return indexOf(iterator, predicate) != -1;
	  }

	  /**
	   * Returns {@code true} if every element returned by {@code iterator} satisfies the given
	   * predicate. If {@code iterator} is empty, {@code true} is returned.
	   */
	  public static <T> boolean all(Iterator<T> iterator, Predicate<? super T> predicate) {
	    checkNotNull(predicate);
	    while (iterator.hasNext()) {
	      T element = iterator.next();
	      if (!predicate.apply(element)) {
	        return false;
	      }
	    }
	    return true;
	  }
	  
	  /**
	   * Returns an iterator over the merged contents of all given {@code iterators}, traversing every
	   * element of the input iterators. Equivalent entries will not be de-duplicated.
	   *
	   * <p>Callers must ensure that the source {@code iterators} are in non-descending order as this
	   * method does not sort its input.
	   *
	   * <p>For any equivalent elements across all {@code iterators}, it is undefined which element is
	   * returned first.
	   *
	   * @since 11.0
	   */
	  @Beta
	  public static <T> UnmodifiableIterator<T> mergeSorted(
	      Iterable<? extends Iterator<? extends T>> iterators, Comparator<? super T> comparator) {
	    checkNotNull(iterators, "iterators");
	    checkNotNull(comparator, "comparator");

	    return new MergingIterator<T>(iterators, comparator);
	  }

	  /**
	   * An iterator that performs a lazy N-way merge, calculating the next value each time the iterator
	   * is polled. This amortizes the sorting cost over the iteration and requires less memory than
	   * sorting all elements at once.
	   *
	   * <p>Retrieving a single element takes approximately O(log(M)) time, where M is the number of
	   * iterators. (Retrieving all elements takes approximately O(N*log(M)) time, where N is the total
	   * number of elements.)
	   */
	  private static class MergingIterator<T> extends UnmodifiableIterator<T> {
	    final Queue<PeekingIterator<T>> queue;

	    public MergingIterator(
	        Iterable<? extends Iterator<? extends T>> iterators,
	        final Comparator<? super T> itemComparator) {
	      // A comparator that's used by the heap, allowing the heap
	      // to be sorted based on the top of each iterator.
	      Comparator<PeekingIterator<T>> heapComparator =
	          new Comparator<PeekingIterator<T>>() {
	            @Override
	            public int compare(PeekingIterator<T> o1, PeekingIterator<T> o2) {
	              return itemComparator.compare(o1.peek(), o2.peek());
	            }
	          };

	      queue = new PriorityQueue<>(2, heapComparator);

	      for (Iterator<? extends T> iterator : iterators) {
	        if (iterator.hasNext()) {
	          queue.add(Iterators.peekingIterator(iterator));
	        }
	      }
	    }

	    @Override
	    public boolean hasNext() {
	      return !queue.isEmpty();
	    }

	    @Override
	    public T next() {
	      PeekingIterator<T> nextIter = queue.remove();
	      T next = nextIter.next();
	      if (nextIter.hasNext()) {
	        queue.add(nextIter);
	      }
	      return next;
	    }
	  }

	  private static class ConcatenatedIterator<T> implements Iterator<T> {
	    /* The last iterator to return an element.  Calls to remove() go to this iterator. */
	    private @Nullable Iterator<? extends T> toRemove;

	    /* The iterator currently returning elements. */
	    private Iterator<? extends T> iterator;

	    /*
	     * We track the "meta iterators," the iterators-of-iterators, below.  Usually, topMetaIterator
	     * is the only one in use, but if we encounter nested concatenations, we start a deque of
	     * meta-iterators rather than letting the nesting get arbitrarily deep.  This keeps each
	     * operation O(1).
	     */

	    private Iterator<? extends Iterator<? extends T>> topMetaIterator;

	    // Only becomes nonnull if we encounter nested concatenations.
	    private @Nullable Deque<Iterator<? extends Iterator<? extends T>>> metaIterators;

	    ConcatenatedIterator(Iterator<? extends Iterator<? extends T>> metaIterator) {
	      iterator = emptyIterator();
	      topMetaIterator = checkNotNull(metaIterator);
	    }

	    // Returns a nonempty meta-iterator or, if all meta-iterators are empty, null.
	    private @Nullable Iterator<? extends Iterator<? extends T>> getTopMetaIterator() {
	      while (topMetaIterator == null || !topMetaIterator.hasNext()) {
	        if (metaIterators != null && !metaIterators.isEmpty()) {
	          topMetaIterator = metaIterators.removeFirst();
	        } else {
	          return null;
	        }
	      }
	      return topMetaIterator;
	    }

	    @Override
	    public boolean hasNext() {
	      while (!checkNotNull(iterator).hasNext()) {
	        // this weird checkNotNull positioning appears required by our tests, which expect
	        // both hasNext and next to throw NPE if an input iterator is null.

	        topMetaIterator = getTopMetaIterator();
	        if (topMetaIterator == null) {
	          return false;
	        }

	        iterator = topMetaIterator.next();

	        if (iterator instanceof ConcatenatedIterator) {
	          // Instead of taking linear time in the number of nested concatenations, unpack
	          // them into the queue
	          @SuppressWarnings("unchecked")
	          ConcatenatedIterator<T> topConcat = (ConcatenatedIterator<T>) iterator;
	          iterator = topConcat.iterator;

	          // topConcat.topMetaIterator, then topConcat.metaIterators, then this.topMetaIterator,
	          // then this.metaIterators

	          if (this.metaIterators == null) {
	            this.metaIterators = new ArrayDeque<>();
	          }
	          this.metaIterators.addFirst(this.topMetaIterator);
	          if (topConcat.metaIterators != null) {
	            while (!topConcat.metaIterators.isEmpty()) {
	              this.metaIterators.addFirst(topConcat.metaIterators.removeLast());
	            }
	          }
	          this.topMetaIterator = topConcat.topMetaIterator;
	        }
	      }
	      return true;
	    }

	    @Override
	    public T next() {
	      if (hasNext()) {
	        toRemove = iterator;
	        return iterator.next();
	      } else {
	        throw new NoSuchElementException();
	      }
	    }

	    @Override
	    public void remove() {
	      CollectPreconditions.checkRemove(toRemove != null);
	      toRemove.remove();
	      toRemove = null;
	    }
	  }


	    /**
	     * Creates an iterator that repeatedly invokes the supplier
	     * while it's a {@code Some} and end on the first {@code None}
	     *
	     * @param supplier A Supplier of iterator values
	     * @param <T> value type
	     * @return A new {@code Iterator}
	     * @throws NullPointerException if supplier produces null value
	     */
	    static <T> Iterator<T> iterate(Supplier<? extends Option<? extends T>> supplier) {
	        Objects.requireNonNull(supplier, "supplier is null");
	        return new Iterator<T>() {
	            Option<? extends T> nextOption;

	            @Override
	            public boolean hasNext() {
	                if (nextOption == null) {
	                    nextOption = supplier.get();
	                }
	                return nextOption.isDefined();
	            }

	            @Override
	            public T next() {
	                if (!hasNext()) {
	                    throw new NoSuchElementException();
	                }
	                final T next =  nextOption.get();
	                nextOption = null;
	                return next;
	            }
	        };
	    }
	    

	    /**
	     * Generates an infinite iterator using a function to calculate the next value
	     * based on the previous.
	     *
	     * @param seed The first value in the iterator
	     * @param f    A function to calculate the next value based on the previous
	     * @param <T>  value type
	     * @return A new {@code Iterator}
	     */
	    static <T> Iterator<T> iterate(T seed, Function<? super T, ? extends T> f) {
	        Objects.requireNonNull(f, "f is null");
	        return new Iterator<T>() {
	            Function<? super T, ? extends T> nextFunc = s -> {
	                nextFunc = f;
	                return seed;
	            };
	            T current = null;

	            @Override
	            public boolean hasNext() {
	                return true;
	            }

	            @Override
	            public T next() {
	                if (!hasNext()) {
	                    throw new NoSuchElementException();
	                }
	                current = nextFunc.apply(current);
	                return current;
	            }
	        };
	    }
	    

	    /**
	     * Creates an infinite iterator returning the given element.
	     *
	     * @param t   An element
	     * @param <T> Element type
	     * @return A new Iterator containing infinite {@code t}'s.
	     */
	    static <T> Iterator<T> continually(T t) {
	        return new Iterator<T>() {
	            @Override
	            public boolean hasNext() {
	                return true;
	            }

	            @Override
	            public T next() {
	                if (!hasNext()) {
	                    throw new NoSuchElementException();
	                }
	                return t;
	            }
	        };
	    }


	    @Override
	    default <U> Iterator<Tuple2<T, U>> zip(Iterable<? extends U> that) {
	        return zipWith(that, Tuple::of);
	    }

	    

	    /**
	     * Inserts an element between all elements of this Iterator.
	     *
	     * @param element An element.
	     * @return an interspersed version of this
	     */
	    default Iterator<T> intersperse(T element) {
	        if (!hasNext()) {
	            return empty();
	        } else {
	            final Iterator<T> that = this;
	            return new Iterator<T>() {

	                boolean insertElement = false;

	                @Override
	                public boolean hasNext() {
	                    return that.hasNext();
	                }

	                @Override
	                public T next() {
	                    if (!hasNext()) {
	                        throw new NoSuchElementException();
	                    }
	                    if (insertElement) {
	                        insertElement = false;
	                        return element;
	                    } else {
	                        insertElement = true;
	                        return that.next();
	                    }
	                }
	            };
	        }
	    }

	    /**
	     * Transforms this {@code Iterator}.
	     *
	     * @param f   A transformation
	     * @param <U> Type of transformation result
	     * @return An instance of type {@code U}
	     * @throws NullPointerException if {@code f} is null
	     */
	    default <U> U transform(Function<? super Iterator<T>, ? extends U> f) {
	        Objects.requireNonNull(f, "f is null");
	        return f.apply(this);
	    }

	    @Override
	    default <U> Iterator<Tuple2<T, U>> zip(Iterable<? extends U> that) {
	        return zipWith(that, Tuple::of);
	    }

	    @Override
	    default <U, R> Iterator<R> zipWith(Iterable<? extends U> that, BiFunction<? super T, ? super U, ? extends R> mapper) {
	        Objects.requireNonNull(that, "that is null");
	        Objects.requireNonNull(mapper, "mapper is null");
	        if (isEmpty()) {
	            return empty();
	        } else {
	            final Iterator<T> it1 = this;
	            final java.util.Iterator<? extends U> it2 = that.iterator();
	            return new Iterator<R>() {
	                @Override
	                public boolean hasNext() {
	                    return it1.hasNext() && it2.hasNext();
	                }

	                @Override
	                public R next() {
	                    if (!hasNext()) {
	                        throw new NoSuchElementException();
	                    }
	                    return mapper.apply(it1.next(), it2.next());
	                }
	            };
	        }
	    }

	    @Override
	    default <U> Iterator<Tuple2<T, U>> zipAll(Iterable<? extends U> that, T thisElem, U thatElem) {
	        Objects.requireNonNull(that, "that is null");
	        final java.util.Iterator<? extends U> thatIt = that.iterator();
	        if (isEmpty() && !thatIt.hasNext()) {
	            return empty();
	        } else {
	            final Iterator<T> thisIt = this;
	            return new Iterator<Tuple2<T, U>>() {
	                @Override
	                public boolean hasNext() {
	                    return thisIt.hasNext() || thatIt.hasNext();
	                }

	                @Override
	                public Tuple2<T, U> next() {
	                    if (!hasNext()) {
	                        throw new NoSuchElementException();
	                    }
	                    final T v1 = thisIt.hasNext() ? thisIt.next() : thisElem;
	                    final U v2 = thatIt.hasNext() ? thatIt.next() : thatElem;
	                    return Tuple.of(v1, v2);
	                }
	            };
	        }
	    }

	    @Override
	    default Iterator<Tuple2<T, Integer>> zipWithIndex() {
	        return zipWithIndex(Tuple::of);
	    }

	    @Override
	    default <U> Iterator<U> zipWithIndex(BiFunction<? super T, ? super Integer, ? extends U> mapper) {
	        Objects.requireNonNull(mapper, "mapper is null");
	        if (isEmpty()) {
	            return empty();
	        } else {
	            final Iterator<T> it1 = this;
	            return new Iterator<U>() {
	                private int index = 0;

	                @Override
	                public boolean hasNext() {
	                    return it1.hasNext();
	                }

	                @Override
	                public U next() {
	                    if (!hasNext()) {
	                        throw new NoSuchElementException();
	                    }
	                    return mapper.apply(it1.next(), index++);
	                }
	            };
	        }
	    }

	    @Override
	    default <T1, T2> Tuple2<Iterator<T1>, Iterator<T2>> unzip(
	            Function<? super T, Tuple2<? extends T1, ? extends T2>> unzipper) {
	        Objects.requireNonNull(unzipper, "unzipper is null");
	        if (!hasNext()) {
	            return Tuple.of(empty(), empty());
	        } else {
	            final Stream<Tuple2<? extends T1, ? extends T2>> source = Stream.ofAll(() -> map(unzipper));
	            return Tuple.of(source.map(t -> (T1) t._1).iterator(), source.map(t -> (T2) t._2).iterator());
	        }
	    }

	    @Override
	    default <T1, T2, T3> Tuple3<Iterator<T1>, Iterator<T2>, Iterator<T3>> unzip3(
	            Function<? super T, Tuple3<? extends T1, ? extends T2, ? extends T3>> unzipper) {
	        Objects.requireNonNull(unzipper, "unzipper is null");
	        if (!hasNext()) {
	            return Tuple.of(empty(), empty(), empty());
	        } else {
	            final Stream<Tuple3<? extends T1, ? extends T2, ? extends T3>> source = Stream.ofAll(map(unzipper));
	            return Tuple.of(source.map(t -> (T1) t._1).iterator(), source.map(t -> (T2) t._2).iterator(), source.map(t -> (T3) t._3).iterator());
	        }
	    }

	    /**
	     * Creates an iterator from a seed value and a function.
	     * The function takes the seed at first.
	     * The function should return {@code None} when it's
	     * done generating elements, otherwise {@code Some} {@code Tuple}
	     * of the value to add to the resulting iterator and
	     * the element for the next call.
	     * <p>
	     * Example:
	     * <pre>
	     * <code>
	     * Iterator.unfold(10, x -&gt; x == 0
	     *                 ? Option.none()
	     *                 : Option.of(new Tuple2&lt;&gt;(x-1, x)));
	     * // List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
	     * </code>
	     * </pre>
	     *
	     * @param <T>  type of seeds and unfolded values
	     * @param seed the start value for the iteration
	     * @param f    the function to get the next step of the iteration
	     * @return a list with the values built up by the iteration
	     * @throws NullPointerException if {@code f} is null
	     */
	    static <T> Iterator<T> unfold(T seed, Function<? super T, Option<Tuple2<? extends T, ? extends T>>> f) {
	        return unfoldLeft(seed, f);
	    }

	    /**
	     * Creates an iterator from a seed value and a function.
	     * The function takes the seed at first.
	     * The function should return {@code None} when it's
	     * done generating elements, otherwise {@code Some} {@code Tuple}
	     * of the value to add to the resulting iterator and
	     * the element for the next call.
	     * <p>
	     * Example:
	     * <pre>
	     * <code>
	     * Iterator.unfoldLeft(10, x -&gt; x == 0
	     *                    ? Option.none()
	     *                    : Option.of(new Tuple2&lt;&gt;(x-1, x)));
	     * // List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
	     * </code>
	     * </pre>
	     *
	     * @param <T>  type of seeds
	     * @param <U>  type of unfolded values
	     * @param seed the start value for the iteration
	     * @param f    the function to get the next step of the iteration
	     * @return a list with the values built up by the iteration
	     * @throws NullPointerException if {@code f} is null
	     */
	    static <T, U> Iterator<U> unfoldLeft(T seed, Function<? super T, Option<Tuple2<? extends T, ? extends U>>> f) {
	        Objects.requireNonNull(f, "f is null");
	        return Stream.<U> ofAll(
	                unfoldRight(seed, f.andThen(tupleOpt -> tupleOpt.map(t -> Tuple.of(t._2, t._1)))))
	                .reverse().iterator();
	    }

	    /**
	     * Creates an iterator from a seed value and a function.
	     * The function takes the seed at first.
	     * The function should return {@code None} when it's
	     * done generating elements, otherwise {@code Some} {@code Tuple}
	     * of the element for the next call and the value to add to the
	     * resulting iterator.
	     * <p>
	     * Example:
	     * <pre>
	     * <code>
	     * Iterator.unfoldRight(10, x -&gt; x == 0
	     *             ? Option.none()
	     *             : Option.of(new Tuple2&lt;&gt;(x, x-1)));
	     * // List(10, 9, 8, 7, 6, 5, 4, 3, 2, 1))
	     * </code>
	     * </pre>
	     *
	     * @param <T>  type of seeds
	     * @param <U>  type of unfolded values
	     * @param seed the start value for the iteration
	     * @param f    the function to get the next step of the iteration
	     * @return a list with the values built up by the iteration
	     * @throws NullPointerException if {@code f} is null
	     */
	    static <T, U> Iterator<U> unfoldRight(T seed, Function<? super T, Option<Tuple2<? extends U, ? extends T>>> f) {
	        Objects.requireNonNull(f, "the unfold iterating function is null");
	        return new Iterator<U>() {
	            private Option<Tuple2<? extends U, ? extends T>> nextVal = f.apply(seed);

	            @Override
	            public boolean hasNext() {
	                return nextVal.isDefined();
	            }

	            @Override
	            public U next() {
	                if (!hasNext()) {
	                    throw new NoSuchElementException();
	                }
	                final U result = nextVal.get()._1;
	                nextVal = f.apply(nextVal.get()._2);
	                return result;
	            }
	        };
	    }

	    // -- Overridden methods of Traversable

	    @Override
	    default Iterator<T> distinct() {
	        if (!hasNext()) {
	            return empty();
	        } else {
	            return new DistinctIterator<>(this, io.vavr.collection.HashSet.empty(), Function.identity());
	        }
	    }

	    @Override
	    default Iterator<T> distinctBy(Comparator<? super T> comparator) {
	        Objects.requireNonNull(comparator, "comparator is null");
	        if (!hasNext()) {
	            return empty();
	        } else {
	            return new DistinctIterator<>(this, TreeSet.empty(comparator), Function.identity());
	        }
	    }

	    @Override
	    default <U> Iterator<T> distinctBy(Function<? super T, ? extends U> keyExtractor) {
	        Objects.requireNonNull(keyExtractor, "keyExtractor is null");
	        if (!hasNext()) {
	            return empty();
	        } else {
	            return new DistinctIterator<>(this, io.vavr.collection.HashSet.empty(), keyExtractor);
	        }
	    }

	    /**
	     * Removes up to n elements from this iterator.
	     *
	     * @param n A number
	     * @return The empty iterator, if {@code n <= 0} or this is empty, otherwise a new iterator without the first n elements.
	     */
	    @Override
	    default Iterator<T> drop(int n) {
	        if (n <= 0) {
	            return this;
	        } else if (!hasNext()) {
	            return empty();
	        } else {
	            final Iterator<T> that = this;
	            return new Iterator<T>() {

	                long count = n;

	                @Override
	                public boolean hasNext() {
	                    while (count > 0 && that.hasNext()) {
	                        that.next(); // discarded
	                        count--;
	                    }
	                    return that.hasNext();
	                }

	                @Override
	                public T next() {
	                    if (!hasNext()) {
	                        throw new NoSuchElementException();
	                    }
	                    return that.next();
	                }
	            };
	        }
	    }

	    @Override
	    default Iterator<T> dropRight(int n) {
	        if (n <= 0) {
	            return this;
	        } else if (!hasNext()) {
	            return empty();
	        } else {
	            final Iterator<T> that = this;
	            return new Iterator<T>() {
	                private io.vavr.collection.Queue<T> queue = io.vavr.collection.Queue.empty();

	                @Override
	                public boolean hasNext() {
	                    while (queue.length() < n && that.hasNext()) {
	                        queue = queue.append(that.next());
	                    }
	                    return queue.length() == n && that.hasNext();
	                }

	                @Override
	                public T next() {
	                    if (!hasNext()) {
	                        throw new NoSuchElementException();
	                    }
	                    final Tuple2<T, io.vavr.collection.Queue<T>> t = queue.append(that.next()).dequeue();
	                    queue = t._2;
	                    return t._1;
	                }
	            };
	        }
	    }

	    @Override
	    default Iterator<T> dropUntil(Predicate<? super T> predicate) {
	        Objects.requireNonNull(predicate, "predicate is null");
	        return dropWhile(predicate.negate());
	    }

	    @Override
	    default Iterator<T> dropWhile(Predicate<? super T> predicate) {
	        Objects.requireNonNull(predicate, "predicate is null");
	        if (!hasNext()) {
	            return empty();
	        } else {
	            final CachedIterator<T> that = new CachedIterator<>(this);
	            while (that.hasNext() && predicate.test(that.touch())) {
	                that.next();
	            }
	            return that;
	        }
	    }

	    /**
	     * Returns an Iterator that contains elements that satisfy the given {@code predicate}.
	     *
	     * @param predicate A predicate
	     * @return A new Iterator
	     */
	    @Override
	    default Iterator<T> filter(Predicate<? super T> predicate) {
	        Objects.requireNonNull(predicate, "predicate is null");
	        if (!hasNext()) {
	            return empty();
	        } else {
	            final Iterator<T> that = this;
	            return new Iterator<T>() {

	                Option<T> next = Option.none();

	                @Override
	                public boolean hasNext() {
	                    while (next.isEmpty() && that.hasNext()) {
	                        final T candidate = that.next();
	                        if (predicate.test(candidate)) {
	                            next = Option.some(candidate);
	                        }
	                    }
	                    return next.isDefined();
	                }

	                @Override
	                public T next() {
	                    if (!hasNext()) {
	                        throw new NoSuchElementException();
	                    }
	                    final T result = next.get();
	                    next = Option.none();
	                    return result;
	                }
	            };
	        }
	    }

	    /**
	     * Returns an Iterator that contains elements that not satisfy the given {@code predicate}.
	     *
	     * @param predicate A predicate
	     * @return A new Iterator
	     */
	    @Override
	    default Iterator<T> filterNot(Predicate<? super T> predicate) {
	        Objects.requireNonNull(predicate, "predicate is null");
	        return filter(predicate.negate());
	    }

	    @Deprecated
	    @Override
	    default Iterator<T> reject(Predicate<? super T> predicate) {
	        Objects.requireNonNull(predicate, "predicate is null");
	        return filter(predicate.negate());
	    }

	    @Override
	    default Option<T> findLast(Predicate<? super T> predicate) {
	        Objects.requireNonNull(predicate, "predicate is null");
	        T last = null;
	        while (hasNext()) {
	            final T elem = next();
	            if (predicate.test(elem)) {
	                last = elem;
	            }
	        }
	        return Option.of(last);
	    }

	    /**
	     * Maps the elements of this Iterator to Iterables and concats their iterators.
	     *
	     * @param mapper A mapper
	     * @param <U>    Component type of the resulting Iterator
	     * @return A new Iterator
	     */
	    // DEV-NOTE: the shorter implementation `concat(map(mapper))` does not perform well
	    @Override
	    default <U> Iterator<U> flatMap(Function<? super T, ? extends Iterable<? extends U>> mapper) {
	        Objects.requireNonNull(mapper, "mapper is null");
	        if (!hasNext()) {
	            return empty();
	        } else {
	            final Iterator<T> that = this;
	            return new Iterator<U>() {

	                final Iterator<? extends T> inputs = that;
	                java.util.Iterator<? extends U> current = java.util.Collections.emptyIterator();

	                @Override
	                public boolean hasNext() {
	                    boolean currentHasNext;
	                    while (!(currentHasNext = current.hasNext()) && inputs.hasNext()) {
	                        current = mapper.apply(inputs.next()).iterator();
	                    }
	                    return currentHasNext;
	                }

	                @Override
	                public U next() {
	                    if (!hasNext()) {
	                        throw new NoSuchElementException();
	                    }
	                    return current.next();
	                }
	            };
	        }
	    }

	    @Override
	    default <U> U foldRight(U zero, BiFunction<? super T, ? super U, ? extends U> f) {
	        Objects.requireNonNull(f, "f is null");
	        return Stream.ofAll(this).foldRight(zero, f);
	    }

	    @Override
	    default T get() {
	        return head();
	    }

	    @Override
	    default <C> Map<C, Iterator<T>> groupBy(Function<? super T, ? extends C> classifier) {
	        return io.vavr.collection.Collections.groupBy(this, classifier, Iterator::ofAll);
	    }

	    @Override
	    default Iterator<Seq<T>> grouped(int size) {
	        return new GroupedIterator<>(this, size, size);
	    }

	    @Override
	    default boolean hasDefiniteSize() {
	        return false;
	    }

	    @Override
	    default T head() {
	        if (!hasNext()) {
	            throw new NoSuchElementException("head() on empty iterator");
	        }
	        return next();
	    }

	    @Override
	    default Iterator<T> init() {
	        if (!hasNext()) {
	            throw new UnsupportedOperationException();
	        } else {
	            return dropRight(1);
	        }
	    }

	    @Override
	    default Option<Iterator<T>> initOption() {
	        return hasNext() ? Option.some(init()) : Option.none();
	    }

	    /**
	     * An {@code Iterator} is computed synchronously.
	     *
	     * @return false
	     */
	    @Override
	    default boolean isAsync() {
	        return false;
	    }

	    @Override
	    default boolean isEmpty() {
	        return !hasNext();
	    }

	    /**
	     * An {@code Iterator} is computed lazily.
	     *
	     * @return true
	     */
	    @Override
	    default boolean isLazy() {
	        return true;
	    }

	    @Override
	    default boolean isTraversableAgain() {
	        return false;
	    }

	    @Override
	    default boolean isSequential() {
	        return true;
	    }

	    @Override
	    default Iterator<T> iterator() {
	        return this;
	    }

	    @Override
	    default T last() {
	        return Collections.last(this);
	    }

	    @Override
	    default int length() {
	        return foldLeft(0, (n, ignored) -> n + 1);
	    }

	    /**
	     * Maps the elements of this Iterator lazily using the given {@code mapper}.
	     *
	     * @param mapper A mapper.
	     * @param <U>    Component type
	     * @return A new Iterator
	     */
	    @Override
	    default <U> Iterator<U> map(Function<? super T, ? extends U> mapper) {
	        Objects.requireNonNull(mapper, "mapper is null");
	        if (!hasNext()) {
	            return empty();
	        } else {
	            final Iterator<T> that = this;
	            return new Iterator<U>() {

	                @Override
	                public boolean hasNext() {
	                    return that.hasNext();
	                }

	                @Override
	                public U next() {
	                    if (!hasNext()) {
	                        throw new NoSuchElementException();
	                    }
	                    return mapper.apply(that.next());
	                }
	            };
	        }
	    }

	    /**
	     * A safe alternative to {@link #next()} that is equivalent to
	     *
	     * <pre>{@code
	     * hasNext() ? Option.some(next()) : Option.none()
	     * }</pre>
	     *
	     * @return a new instance of {@link Option}
	     */
	    default Option<T> nextOption() {
	        return hasNext() ? Option.some(next()) : Option.none();
	    }

	    @Override
	    default Iterator<T> orElse(Iterable<? extends T> other) {
	        return isEmpty() ? ofAll(other) : this;
	    }

	    @Override
	    default Iterator<T> orElse(Supplier<? extends Iterable<? extends T>> supplier) {
	        return isEmpty() ? ofAll(supplier.get()) : this;
	    }

	    @Override
	    default Tuple2<Iterator<T>, Iterator<T>> partition(Predicate<? super T> predicate) {
	        Objects.requireNonNull(predicate, "predicate is null");
	        if (!hasNext()) {
	            return Tuple.of(empty(), empty());
	        } else {
	            final Tuple2<Iterator<T>, Iterator<T>> dup = IteratorModule.duplicate(this);
	            return Tuple.of(dup._1.filter(predicate), dup._2.filterNot(predicate));
	        }
	    }

	    @Override
	    default Iterator<T> peek(Consumer<? super T> action) {
	        Objects.requireNonNull(action, "action is null");
	        if (!hasNext()) {
	            return empty();
	        } else {
	            final Iterator<T> that = this;
	            return new Iterator<T>() {
	                @Override
	                public boolean hasNext() {
	                    return that.hasNext();
	                }

	                @Override
	                public T next() {
	                    if (!hasNext()) {
	                        throw new NoSuchElementException();
	                    }
	                    final T next = that.next();
	                    action.accept(next);
	                    return next;
	                }
	            };
	        }
	    }

	    @Override
	    default T reduceLeft(BiFunction<? super T, ? super T, ? extends T> op) {
	        Objects.requireNonNull(op, "op is null");
	        if (isEmpty()) {
	            throw new NoSuchElementException("reduceLeft on Nil");
	        } else {
	            T xs = next();
	            while (hasNext()) {
	                xs = op.apply(xs, next());
	            }
	            return xs;
	        }
	    }

	    @Override
	    default T reduceRight(BiFunction<? super T, ? super T, ? extends T> op) {
	        Objects.requireNonNull(op, "op is null");
	        if (isEmpty()) {
	            throw new NoSuchElementException("reduceRight on Nil");
	        } else {
	            final Stream<T> reversed = Stream.ofAll(this).reverse();
	            return reversed.reduceLeft((xs, x) -> op.apply(x, xs));
	        }
	    }

	    @Override
	    default Iterator<T> replace(T currentElement, T newElement) {
	        if (!hasNext()) {
	            return empty();
	        } else {
	            final Iterator<T> that = this;
	            return new Iterator<T>() {
	                boolean isFirst = true;

	                @Override
	                public boolean hasNext() {
	                    return that.hasNext();
	                }

	                @Override
	                public T next() {
	                    if (!hasNext()) {
	                        throw new NoSuchElementException();
	                    }
	                    final T elem = that.next();
	                    if (isFirst && Objects.equals(currentElement, elem)) {
	                        isFirst = false;
	                        return newElement;
	                    } else {
	                        return elem;
	                    }
	                }
	            };
	        }
	    }

	    @Override
	    default Iterator<T> replaceAll(T currentElement, T newElement) {
	        if (!hasNext()) {
	            return empty();
	        } else {
	            final Iterator<T> that = this;
	            return new Iterator<T>() {

	                @Override
	                public boolean hasNext() {
	                    return that.hasNext();
	                }

	                @Override
	                public T next() {
	                    if (!hasNext()) {
	                        throw new NoSuchElementException();
	                    }
	                    final T elem = that.next();
	                    if (Objects.equals(currentElement, elem)) {
	                        return newElement;
	                    } else {
	                        return elem;
	                    }
	                }
	            };
	        }
	    }

	    @Override
	    default Iterator<T> retainAll(Iterable<? extends T> elements) {
	        return io.vavr.collection.Collections.retainAll(this, elements);
	    }

	    @Override
	    default Traversable<T> scan(T zero, BiFunction<? super T, ? super T, ? extends T> operation) {
	        return scanLeft(zero, operation);
	    }

	    @Override
	    default <U> Iterator<U> scanLeft(U zero, BiFunction<? super U, ? super T, ? extends U> operation) {
	        Objects.requireNonNull(operation, "operation is null");
	        if (isEmpty()) {
	            return of(zero);
	        } else {
	            final Iterator<T> that = this;
	            return new Iterator<U>() {

	                boolean isFirst = true;
	                U acc = zero;

	                @Override
	                public boolean hasNext() {
	                    return isFirst || that.hasNext();
	                }

	                @Override
	                public U next() {
	                    if (!hasNext()) {
	                        throw new NoSuchElementException();
	                    }
	                    if (isFirst) {
	                        isFirst = false;
	                        return acc;
	                    } else {
	                        acc = operation.apply(acc, that.next());
	                        return acc;
	                    }
	                }
	            };
	        }
	    }

	    // not lazy!
	    @Override
	    default <U> Iterator<U> scanRight(U zero, BiFunction<? super T, ? super U, ? extends U> operation) {
	        Objects.requireNonNull(operation, "operation is null");
	        if (isEmpty()) {
	            return of(zero);
	        } else {
	            return io.vavr.collection.Collections.scanRight(this, zero, operation, Function.identity());
	        }
	    }

	    @Override
	    default Iterator<Seq<T>> slideBy(Function<? super T, ?> classifier) {
	        Objects.requireNonNull(classifier, "classifier is null");
	        if (!hasNext()) {
	            return empty();
	        } else {
	            final CachedIterator<T> source = new CachedIterator<>(this);
	            return new Iterator<Seq<T>>() {
	                private Stream<T> next = null;

	                @Override
	                public boolean hasNext() {
	                    if (next == null && source.hasNext()) {
	                        final Object key = classifier.apply(source.touch());
	                        final java.util.List<T> acc = new ArrayList<>();
	                        while (source.hasNext() && key.equals(classifier.apply(source.touch()))) {
	                            acc.add(source.next());
	                        }
	                        next = Stream.ofAll(acc);
	                    }
	                    return next != null;
	                }

	                @Override
	                public Stream<T> next() {
	                    if (!hasNext()) {
	                        throw new NoSuchElementException();
	                    }
	                    final Stream<T> result = next;
	                    next = null;
	                    return result;
	                }
	            };
	        }
	    }

	    @Override
	    default Iterator<Seq<T>> sliding(int size) {
	        return sliding(size, 1);
	    }

	    @Override
	    default Iterator<Seq<T>> sliding(int size, int step) {
	        return new GroupedIterator<>(this, size, step);
	    }

	    @Override
	    default Tuple2<Iterator<T>, Iterator<T>> span(Predicate<? super T> predicate) {
	        Objects.requireNonNull(predicate, "predicate is null");
	        if (!hasNext()) {
	            return Tuple.of(empty(), empty());
	        } else {
	            final Stream<T> that = Stream.ofAll(this);
	            return Tuple.of(that.iterator().takeWhile(predicate), that.iterator().dropWhile(predicate));
	        }
	    }


	    @Override
	    default String stringPrefix() {
	        return "Iterator";
	    }

	    @Override
	    default Iterator<T> tail() {
	        if (!hasNext()) {
	            throw new UnsupportedOperationException();
	        } else {
	            next(); // remove first element
	            return this;
	        }
	    }

	    @Override
	    default Option<Iterator<T>> tailOption() {
	        if (hasNext()) {
	            next();
	            return Option.some(this);
	        } else {
	            return Option.none();
	        }
	    }

	    /**
	     * Take the first n elements from this iterator.
	     *
	     * @param n A number
	     * @return The empty iterator, if {@code n <= 0} or this is empty, otherwise a new iterator without the first n elements.
	     */
	    @Override
	    default Iterator<T> take(int n) {
	        if (n <= 0 || !hasNext()) {
	            return empty();
	        } else {
	            final Iterator<T> that = this;
	            return new Iterator<T>() {

	                long count = n;

	                @Override
	                public boolean hasNext() {
	                    return count > 0 && that.hasNext();
	                }

	                @Override
	                public T next() {
	                    if (!hasNext()) {
	                        throw new NoSuchElementException();
	                    }
	                    count--;
	                    return that.next();
	                }
	            };
	        }
	    }

	    @Override
	    default Iterator<T> takeRight(int n) {
	        if (n <= 0) {
	            return empty();
	        } else {
	            final Iterator<T> that = this;
	            return new Iterator<T>() {
	                private io.vavr.collection.Queue<T> queue = io.vavr.collection.Queue.empty();

	                @Override
	                public boolean hasNext() {
	                    while (that.hasNext()) {
	                        queue = queue.enqueue(that.next());
	                        if (queue.length() > n) {
	                            queue = queue.dequeue()._2;
	                        }
	                    }
	                    return queue.length() > 0;
	                }

	                @Override
	                public T next() {
	                    if (!hasNext()) {
	                        throw new NoSuchElementException();
	                    }
	                    final Tuple2<T, io.vavr.collection.Queue<T>> t = queue.dequeue();
	                    queue = t._2;
	                    return t._1;
	                }
	            };
	        }
	    }

	    @Override
	    default Iterator<T> takeUntil(Predicate<? super T> predicate) {
	        Objects.requireNonNull(predicate, "predicate is null");
	        return takeWhile(predicate.negate());
	    }

	    @Override
	    default Iterator<T> takeWhile(Predicate<? super T> predicate) {
	        Objects.requireNonNull(predicate, "predicate is null");
	        if (!hasNext()) {
	            return empty();
	        } else {
	            final Iterator<T> that = this;
	            return new Iterator<T>() {

	                private T next;
	                private boolean cached = false;
	                private boolean finished = false;

	                @Override
	                public boolean hasNext() {
	                    if (cached) {
	                        return true;
	                    } else if (finished) {
	                        return false;
	                    } else if (that.hasNext()) {
	                        next = that.next();
	                        if (predicate.test(next)) {
	                            cached = true;
	                            return true;
	                        }
	                    }
	                    finished = true;
	                    return false;
	                }

	                @Override
	                public T next() {
	                    if (!hasNext()) {
	                        throw new NoSuchElementException();
	                    }
	                    cached = false;
	                    return next;
	                }
	            };
	        }
	    }

	    /**
	     * Converts this to a {@link Seq}.
	     *
	     * @return A new {@link Seq}.
	     */
	    default Seq<T> toSeq() {
	        return toList();
	    }
	}

	final class ArrayIterator<T> implements Iterator<T> {

	    private final T[] elements;
	    private int index = 0;

	    ArrayIterator(T[] elements) {
	        this.elements = elements;
	    }

	    @Override
	    public boolean hasNext() {
	        return index < elements.length;
	    }

	    @Override
	    public T next() {
	        try {
	            return elements[index++];
	        } catch(IndexOutOfBoundsException x) {
	            index--;
	            throw new NoSuchElementException();
	        }
	    }

	    @Override
	    public void forEachRemaining(Consumer<? super T> action) {
	        Objects.requireNonNull(action);
	        while (index < elements.length) {
	            action.accept(elements[index++]);
	        }
	    }

	    @Override
	    public String toString() {
	        return "ArrayIterator";
	    }
	}

	final class CachedIterator<T> implements Iterator<T> {

	    private final Iterator<T> that;

	    private T next;
	    private boolean cached = false;

	    CachedIterator(Iterator<T> that) {
	        this.that = that;
	    }

	    @Override
	    public boolean hasNext() {
	        return cached || that.hasNext();
	    }

	    @Override
	    public T next() {
	        if (!hasNext()) {
	            throw new NoSuchElementException();
	        }
	        if (cached) {
	            T result = next;
	            next = null;
	            cached = false;
	            return result;
	        } else {
	            return that.next();
	        }
	    }

	    T touch() {
	        next = next();
	        cached = true;
	        return next;
	    }

	    @Override
	    public String toString() {
	        return "CachedIterator";
	    }
	}

	interface IteratorModule {
	    /**
	     * Creates two new iterators that both iterates over the same elements as
	     * this iterator and in the same order. The duplicate iterators are
	     * considered equal if they are positioned at the same element.
	     * <p>
	     * Given that most methods on iterators will make the original iterator
	     * unfit for further use, this methods provides a reliable way of calling
	     * multiple such methods on an iterator.
	     *
	     * @return a pair of iterators
	     */
	    static <T> Tuple2<Iterator<T>, Iterator<T>> duplicate(Iterator<T> iterator) {
	        final java.util.Queue<T> gap = new java.util.LinkedList<>();
	        final AtomicReference<Iterator<T>> ahead = new AtomicReference<>();
	        class Partner implements Iterator<T> {

	            @Override
	            public boolean hasNext() {
	                return (this != ahead.get() && !gap.isEmpty()) || iterator.hasNext();
	            }

	            @Override
	            public T next() {
	                if (gap.isEmpty()) {
	                    ahead.set(this);
	                }
	                if (this == ahead.get()) {
	                    final T element = iterator.next();
	                    gap.add(element);
	                    return element;
	                } else {
	                    return gap.poll();
	                }
	            }
	        }
	        return Tuple.of(new Partner(), new Partner());
	    }
	}

	final class ConcatIterator<T> implements Iterator<T> {

	    private static final class Iterators<T> {

	        private final java.util.Iterator<T> head;
	        private Iterators<T> tail;

	        @SuppressWarnings("unchecked")
	        Iterators(java.util.Iterator<? extends T> head) {
	            this.head = (java.util.Iterator<T>) head;
	        }
	    }

	    private Iterators<T> curr;
	    private Iterators<T> last;
	    private boolean nextCalculated = false;

	    ConcatIterator(java.util.Iterator<? extends java.util.Iterator<? extends T>> iterators) {
	        this.curr = this.last = iterators.hasNext() ? new Iterators<>(iterators.next()) : null;
	        while (iterators.hasNext()) {
	            this.last = this.last.tail = new Iterators<>(iterators.next());
	        }
	    }

	    @Override
	    public boolean hasNext() {
	        if (nextCalculated) {
	            return curr != null;
	        } else {
	            nextCalculated = true;
	            while (true) {
	                if (curr.head.hasNext()) {
	                    return true;
	                } else {
	                    curr = curr.tail;
	                    if (curr == null) {
	                        last = null; // release reference
	                        return false;
	                    }
	                }
	            }
	        }
	    }

	    @Override
	    public T next() {
	        if (!hasNext()) {
	            throw new NoSuchElementException();
	        }
	        nextCalculated = false;
	        return curr.head.next();
	    }

	    @Override
	    public Iterator<T> concat(java.util.Iterator<? extends T> that) {
	        if (curr == null) {
	            nextCalculated = false;
	            curr = last = new Iterators<>(that);
	        } else {
	            last = last.tail = new Iterators<>(that);
	        }
	        return this;
	    }

	    @Override
	    public String toString() {
	        return "ConcatIterator";
	    }
	}

	final class DistinctIterator<T, U> implements Iterator<T> {

	    private final Iterator<? extends T> that;
	    private io.vavr.collection.Set<U> known;
	    private final Function<? super T, ? extends U> keyExtractor;
	    private boolean nextDefined = false;
	    private T next;

	    DistinctIterator(Iterator<? extends T> that, Set<U> set, Function<? super T, ? extends U> keyExtractor) {
	        this.that = that;
	        this.known = set;
	        this.keyExtractor = keyExtractor;
	    }

	    @Override
	    public boolean hasNext() {
	        return nextDefined || searchNext();
	    }

	    private boolean searchNext() {
	        while (that.hasNext()) {
	            final T elem = that.next();
	            final U key = keyExtractor.apply(elem);
	            if (!known.contains(key)) {
	                known = known.add(key);
	                nextDefined = true;
	                next = elem;
	                return true;
	            }
	        }
	        return false;
	    }

	    @Override
	    public T next() {
	        if (!hasNext()) {
	            throw new NoSuchElementException();
	        }
	        final T result = next;
	        nextDefined = false;
	        next = null;
	        return result;
	    }

	    @Override
	    public String toString() {
	        return "DistinctIterator";
	    }
	}

	final class EmptyIterator implements Iterator<Object> {

	    static final EmptyIterator INSTANCE = new EmptyIterator();

	    private EmptyIterator() {}

	    @Override
	    public boolean hasNext() {
	        return false;
	    }

	    @Override
	    public Object next() {
	        throw new NoSuchElementException();
	    }

	    @Override
	    public void forEachRemaining(Consumer<? super Object> action) {
	        Objects.requireNonNull(action);
	    }

	    @Override
	    public String toString() {
	        return "EmptyIterator";
	    }
	}

	final class GroupedIterator<T> implements Iterator<Seq<T>> {

	    private final Iterator<T> that;
	    private final int size;
	    private final int step;
	    private final int gap;
	    private final int preserve;

	    private Object[] buffer;

	    GroupedIterator(Iterator<T> that, int size, int step) {
	        if (size < 1 || step < 1) {
	            throw new IllegalArgumentException("size (" + size + ") and step (" + step + ") must both be positive");
	        }
	        this.that = that;
	        this.size = size;
	        this.step = step;
	        this.gap = Math.max(step - size, 0);
	        this.preserve = Math.max(size - step, 0);
	        this.buffer = take(that, new Object[size], 0, size);
	    }

	    @Override
	    public boolean hasNext() {
	        return buffer.length > 0;
	    }

	    @Override
	    public Seq<T> next() {
	        if (buffer.length == 0) {
	            throw new NoSuchElementException();
	        }
	        final Object[] result = buffer;
	        if (that.hasNext()) {
	            buffer = new Object[size];
	            if (preserve > 0) {
	                System.arraycopy(result, step, buffer, 0, preserve);
	            }
	            if (gap > 0) {
	                drop(that, gap);
	                buffer = take(that, buffer, preserve, size);
	            } else {
	                buffer = take(that, buffer, preserve, step);
	            }
	        } else {
	            buffer = new Object[0];
	        }
	        return Array.wrap(result);
	    }

	    @Override
	    public String toString() {
	        return "GroupedIterator";
	    }

	    private static void drop(Iterator<?> source, int count) {
	        for (int i = 0; i < count && source.hasNext(); i++) {
	            source.next();
	        }
	    }

	    private static Object[] take(Iterator<?> source, Object[] target, int offset, int count) {
	        int i = offset;
	        while (i < count + offset && source.hasNext()) {
	            target[i] = source.next();
	            i++;
	        }
	        if (i < target.length) {
	            final Object[] result = new Object[i];
	            System.arraycopy(target, 0, result, 0, i);
	            return result;
	        } else {
	            return target;
	        }
	    }
	}

	final class SingletonIterator<T> implements Iterator<T> {

	    private final T element;
	    private boolean hasNext = true;

	    SingletonIterator(T element) {
	        this.element = element;
	    }

	    @Override
	    public boolean hasNext() {
	        return hasNext;
	    }

	    @Override
	    public T next() {
	        if (!hasNext()) {
	            throw new NoSuchElementException();
	        }
	        hasNext = false;
	        return element;
	    }

	    @Override
	    public void forEachRemaining(Consumer<? super T> action) {
	        Objects.requireNonNull(action);
	        if (hasNext) {
	            action.accept(element);
	            hasNext = false;
	        }
	    }

	    @Override
	    public String toString() {
	        return "SingletonIterator";
	    }
	}
}
