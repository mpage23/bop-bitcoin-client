/*
 * Copyright 2013 bits of proof zrt.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bitsofproof.supernode.common;

import java.util.List;

/**
 * A utility class to calculate merkle root
 * 
 * @param <T>
 */
public abstract class BinaryAggregator<T>
{
	public abstract T merge (T a, T b);

	public T aggregate (List<T> list)
	{
		int offset = 0;
		for ( int size = list.size (); size > 1; size = (size + 1) / 2 )
		{
			for ( int left = 0; left < size; left += 2 )
			{
				int right = Math.min (left + 1, size - 1);
				T a = list.get (offset + left);
				T b = list.get (offset + right);
				list.add (merge (a, b));
			}
			offset += size;
		}
		return list.get (list.size () - 1);
	}
}
