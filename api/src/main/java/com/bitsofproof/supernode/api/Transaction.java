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
package com.bitsofproof.supernode.api;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import com.bitsofproof.supernode.common.ByteUtils;
import com.bitsofproof.supernode.common.Hash;
import com.bitsofproof.supernode.common.ScriptFormat;
import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.common.WireFormat;
import com.google.protobuf.ByteString;

/**
 * A Bitcoin Transaction in an object
 */
public class Transaction implements Serializable, Cloneable
{
	public static Builder create()
	{
		return new Builder();
	}

	public static class Builder
	{
		private long version = 1;

		private long lockTime = 0;

		private List<TransactionInput> inputs;
		private List<TransactionOutput> outputs;

		public Builder inputs(Iterable<TransactionInput> inputs)
		{
//			inputs.forEach(this.inputs::add);
			return this;
		}

		public Builder inputs(TransactionInput... inputs)
		{
			this.inputs = Arrays.asList(inputs);
			return this;
		}

		public Builder outputs(Iterable<TransactionOutput> outputs)
		{
//			outputs.forEach(this.outputs::add);
			return this;
		}

		public Builder outputs(TransactionOutput... outputs)
		{
			this.outputs = Arrays.asList(outputs);
			return this;
		}

		public Builder version(long v)
		{
			version = v;
			return this;
		}

		public Builder lockTime(long lt)
		{
			lockTime = lt;
			return this;
		}

		public Transaction build()
		{
			Objects.requireNonNull(inputs, "Transaction must have inputs");
			Objects.requireNonNull(outputs, "Transaction must have outputs");

			return new Transaction(version, lockTime, inputs, outputs);
		}
	}

	private static final long serialVersionUID = 690918485496086537L;

	private long version = 1;

	private long lockTime = 0;

	private List<TransactionInput> inputs;
	private List<TransactionOutput> outputs;

	// below are not part of P2P wire format
	private boolean expired = false;
	private String blockHash;
	private int height = 0;
	private long blocktime = new Date ().getTime () / 1000;
	// below is not part of server messages, but populated in the client library
	private String hash;
	private String offendingTx;

	/**
	 * create a coin base transaction crediting the given address with a value and block height This is used in automated tests only.
	 */
	public static Transaction createCoinbase (Address address, long value, int blockHeight) throws ValidationException
	{
		Transaction cb = new Transaction ();

		cb.setInputs (new ArrayList<TransactionInput> ());
		cb.setOutputs (new ArrayList<TransactionOutput> ());

		TransactionOutput out = new TransactionOutput ();
		out.setValue (value);
		cb.getOutputs ().add (out);

		out.setScript (address.getAddressScript ());

		TransactionInput in = new TransactionInput ();
		in.setSourceHash (Hash.ZERO_HASH_STRING);
		in.setIx (0);
		cb.getInputs ().add (in);

		ScriptFormat.Writer writer = new ScriptFormat.Writer ();
		writer.writeInt32 (blockHeight);
		in.setScript (writer.toByteArray ());

		cb.computeHash ();
		return cb;
	}

	public Transaction()
	{
	}

	public Transaction(long version,
					   long lockTime,
					   List<TransactionInput> inputs,
					   List<TransactionOutput> outputs)
	{
		this.version = version;
		this.lockTime = lockTime;
		this.inputs = inputs;
		this.outputs = outputs;
	}

	/**
	 * get transaction version
	 * 
	 * @return version
	 */
	public long getVersion ()
	{
		return version;
	}

	/**
	 * get hash of the block this transaction is embedded into. Note that this is not part of the protocol, but is filled by the server while retrieving a
	 * transaction in context of a block A transaction alone might not have this filled.
	 */
	public String getBlockHash ()
	{
		return blockHash;
	}

	/**
	 * Set the block hash this transaction is in
	 */
	public void setBlockHash (String blockHash)
	{
		this.blockHash = blockHash;
	}

	/**
	 * Set transaction version
	 */
	public void setVersion (long version)
	{
		this.version = version;
	}

	/**
	 * The time point after which this transaction can be included into the block chain. It is interpreted as block height if < 500000000 and as seconds in Unix
	 * epoch if >= 500000000
	 * 
	 * This is only relevant if sequence number is not 0xffffffff (final)
	 */
	public long getLockTime ()
	{
		return lockTime;
	}

	/**
	 * Set the time point after which this transaction can be included into the block chain. It is interpreted as block height if < 500000000 and as seconds in
	 * Unix epoch if >= 500000000
	 * 
	 * This is only relevant if sequence number is not 0xffffffff (final)
	 */
	public void setLockTime (long lockTime)
	{
		this.lockTime = lockTime;
	}

	/**
	 * get the time stamp of the block containing this transaction. Note that this is not part of the protocol but filled by the server if the transaction is
	 * retrieved in the context of a block
	 */
	public long getBlocktime ()
	{
		return blocktime;
	}

	/**
	 * set the time stamp of the block containing this transaction. Note that this is not part of the protocol but filled by the server if the transaction is
	 * retrieved in the context of a block
	 */
	public void setBlocktime (long blocktime)
	{
		this.blocktime = blocktime;
	}

	/**
	 * compute and set the transaction hash by computing digest of its entire content in wire format
	 */
	public void computeHash ()
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		toWire (writer);
		WireFormat.Reader reader = new WireFormat.Reader (writer.toByteArray ());
		hash = reader.hash ().toString ();

		long ix = 0;
		for ( TransactionOutput o : outputs )
		{
			o.setIx (ix);
			o.setTxHash (hash);
			++ix;
		}
	}

	/**
	 * get the transaction hash. Note that this also computes it if it were not yest available.
	 * 
	 * @return transaction hash
	 */
	public String getHash ()
	{
		if ( hash == null )
		{
			computeHash ();
		}
		return hash;
	}

	/**
	 * set the transaction hash to an arbirtary value
	 */
	public void setHash (String hash)
	{
		this.hash = hash;
	}

	/**
	 * get the transaction inputs
	 */
	public List<TransactionInput> getInputs ()
	{
		return inputs;
	}

	/**
	 * set the transaction inputs
	 */
	public void setInputs (List<TransactionInput> inputs)
	{
		this.inputs = inputs;
	}

	/**
	 * get transaction outputs
	 */
	public List<TransactionOutput> getOutputs ()
	{
		return outputs;
	}

	/**
	 * set transaction outputs
	 */
	public void setOutputs (List<TransactionOutput> outputs)
	{
		this.outputs = outputs;
	}

	/**
	 * a flag set by the server if this transaction was removed from memory pool because it was not included into a block for longer than the set timeout.
	 */
	public boolean isExpired ()
	{
		return expired;
	}

	/**
	 * set expiry flag on transaction
	 */
	public void setExpired (boolean expired)
	{
		this.expired = expired;
	}

	/**
	 * an other transaction hash set by the server if that other transaction double spent inputs of this.
	 */
	public String getOffendingTx ()
	{
		return offendingTx;
	}

	/**
	 * an other transaction hash set by the server if that other transaction double spent inputs of this.
	 */
	public void setOffendingTx (String offendingTx)
	{
		this.offendingTx = offendingTx;
	}

	/**
	 * Block height this transaction is included into, set by the server is available
	 */
	public int getHeight ()
	{
		return height;
	}

	/**
	 * Block height this transaction is included into, set by the server is available
	 */
	public void setHeight (int height)
	{
		this.height = height;
	}

	/**
	 * write the transaction to a wire format writer
	 */
	public void toWire (WireFormat.Writer writer)
	{
		writer.writeUint32 (version);
		if ( inputs != null )
		{
			writer.writeVarInt (inputs.size ());
			for ( TransactionInput input : inputs )
			{
				input.toWire (writer);
			}
		}
		else
		{
			writer.writeVarInt (0);
		}

		if ( outputs != null )
		{
			writer.writeVarInt (outputs.size ());
			for ( TransactionOutput output : outputs )
			{
				output.toWire (writer);
			}
		}
		else
		{
			writer.writeVarInt (0);
		}

		writer.writeUint32 (lockTime);
	}

	/**
	 * Recreate a transaction object from a wire format reader
	 */
	public static Transaction fromWire (WireFormat.Reader reader)
	{
		Transaction t = new Transaction ();

		int cursor = reader.getCursor ();

		t.version = reader.readUint32 ();
		long nin = reader.readVarInt ();
		if ( nin > 0 )
		{
			t.inputs = new ArrayList<TransactionInput> ();
			for ( int i = 0; i < nin; ++i )
			{
				t.inputs.add (TransactionInput.fromWire (reader));
			}
		}
		else
		{
			t.inputs = null;
		}

		long nout = reader.readVarInt ();
		if ( nout > 0 )
		{
			t.outputs = new ArrayList<TransactionOutput> ();
			for ( long i = 0; i < nout; ++i )
			{
				t.outputs.add (TransactionOutput.fromWire (reader));
			}
		}
		else
		{
			t.outputs = null;
		}

		t.lockTime = reader.readUint32 ();

		t.hash = reader.hash (cursor, reader.getCursor () - cursor).toString();

		return t;
	}

	/**
	 * Recreate a transaction object from a wire format dump in hexadecimal format
	 */
	public static Transaction fromWireDump (String dump)
	{
		return fromWire (new WireFormat.Reader (ByteUtils.fromHex (dump)));
	}

	/**
	 * dump the transaction in wire format to a hexadecimal string
	 */
	public String toWireDump ()
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		toWire (writer);
		return ByteUtils.toHex (writer.toByteArray ());
	}

	@Override
	public Transaction clone () throws CloneNotSupportedException
	{
		Transaction t = (Transaction) super.clone ();

		t.version = version;
		if ( inputs != null )
		{
			t.inputs = new ArrayList<TransactionInput> (inputs.size ());
			for ( TransactionInput i : inputs )
			{
				t.inputs.add (i.clone ());
			}
		}
		if ( outputs != null )
		{
			t.outputs = new ArrayList<TransactionOutput> (outputs.size ());
			for ( TransactionOutput o : outputs )
			{
				t.outputs.add (o.clone ());
			}
		}

		t.lockTime = lockTime;

		t.hash = hash;

		t.blockHash = blockHash;

		t.blocktime = blocktime;

		return t;
	}

	/**
	 * Create a protobuf message for the transaction as used to communicate with the server.
	 */
	public BCSAPIMessage.Transaction toProtobuf ()
	{
		BCSAPIMessage.Transaction.Builder builder = BCSAPIMessage.Transaction.newBuilder ();
		builder.setLocktime ((int) lockTime);
		builder.setVersion ((int) version);
		if ( inputs != null )
		{
			for ( TransactionInput i : inputs )
			{
				builder.addInputs (i.toProtobuf ());
			}
		}
		if ( outputs != null && outputs.size () > 0 )
		{
			for ( TransactionOutput o : outputs )
			{
				builder.addOutputs (o.toProtobuf ());
			}
		}
		if ( blockHash != null )
		{
			builder.setBlock (ByteString.copyFrom (new Hash (blockHash).toByteArray()));
		}
		if ( expired )
		{
			builder.setExpired (true);
		}
		if ( height != 0 )
		{
			builder.setHeight (height);
		}
		if ( blocktime != 0 )
		{
			builder.setBlocktime ((int) blocktime);
		}
		return builder.build ();
	}

	/**
	 * Recreate the transaction object from a protobuf message
	 */
	public static Transaction fromProtobuf (BCSAPIMessage.Transaction pt)
	{
		Transaction transaction = new Transaction ();
		transaction.setLockTime (pt.getLocktime ());
		transaction.setVersion (pt.getVersion ());
		if ( pt.getInputsCount () > 0 )
		{
			transaction.setInputs (new ArrayList<TransactionInput> ());
			for ( BCSAPIMessage.TransactionInput i : pt.getInputsList () )
			{
				transaction.getInputs ().add(TransactionInput.fromProtobuf(i));
			}
		}

		if ( pt.getOutputsCount () > 0 )
		{
			transaction.setOutputs (new ArrayList<TransactionOutput> ());
			for ( BCSAPIMessage.TransactionOutput o : pt.getOutputsList () )
			{
				transaction.getOutputs ().add(TransactionOutput.fromProtobuf(o));
			}
		}
		if ( pt.hasBlock () )
		{
			transaction.blockHash = new Hash (pt.getBlock ().toByteArray ()).toString ();
		}
		if ( pt.hasExpired () && pt.getExpired () )
		{
			transaction.expired = true;
		}
		if ( pt.hasHeight () )
		{
			transaction.height = pt.getHeight ();
		}
		if ( pt.hasBlocktime () )
		{
			transaction.blocktime = pt.getBlocktime ();
		}
		transaction.computeHash ();
		return transaction;
	}

	/**
	 * compute the digest for a signature
	 * 
	 * @param inr
	 *            - the input we want to sign for
	 * @param hashType
	 *            - the type of signature. SIGHASH_ALL: sign all inputs and outputs (default), SIGHASH_NONE: sign any output, SIGHASH_SINGLE: sign one of the
	 *            outputs, SIGHASH_ANYONECANPAY: let others add inputs
	 * @param script
	 *            - the input script to sign
	 * @return - digest to sign
	 * @throws ValidationException
	 */
	public byte[] hashTransaction (int inr, int hashType, byte[] script) throws ValidationException
	{
		Transaction copy;
		try
		{
			copy = clone ();
		}
		catch ( CloneNotSupportedException e1 )
		{
			return null;
		}

		// implicit SIGHASH_ALL
		int i = 0;
		for ( TransactionInput in : copy.getInputs () )
		{
			if ( i == inr )
			{
				in.setScript (script);
			}
			else
			{
				in.setScript (new byte[0]);
			}
			++i;
		}

		if ( (hashType & 0x1f) == ScriptFormat.SIGHASH_NONE )
		{
			copy.getOutputs ().clear();
			i = 0;
			for ( TransactionInput in : copy.getInputs () )
			{
				if ( i != inr )
				{
					in.setSequence (0);
				}
				++i;
			}
		}
		else if ( (hashType & 0x1f) == ScriptFormat.SIGHASH_SINGLE )
		{
			int onr = inr;
			if ( onr >= copy.getOutputs ().size() )
			{
				// this is a Satoshi client bug.
				// This case should throw an error but it instead retuns 1 that is not checked and interpreted as below
				return ByteUtils.fromHex ("0100000000000000000000000000000000000000000000000000000000000000");
			}
			for ( i = copy.getOutputs ().size() - 1; i > onr; --i )
			{
				copy.getOutputs ().remove(i);
			}
			for ( i = 0; i < onr; ++i )
			{
				copy.getOutputs ().get(i).setScript (new byte[0]);
				copy.getOutputs ().get(i).setValue (-1L);
			}
			i = 0;
			for ( TransactionInput in : copy.getInputs () )
			{
				if ( i != inr )
				{
					in.setSequence (0);
				}
				++i;
			}
		}
		if ( (hashType & ScriptFormat.SIGHASH_ANYONECANPAY) != 0 )
		{
			List<TransactionInput> oneIn = new ArrayList<> ();
			oneIn.add(copy.getInputs().get(inr));
			copy.setInputs(oneIn);
		}

		WireFormat.Writer writer = new WireFormat.Writer ();
		copy.toWire (writer);

		byte[] txwire = writer.toByteArray ();
		byte[] hash = null;
		try
		{
			MessageDigest a = MessageDigest.getInstance ("SHA-256");
			a.update (txwire);
			a.update (new byte[] { (byte) (hashType & 0xff), 0, 0, 0 });
			hash = a.digest (a.digest ());
		}
		catch ( NoSuchAlgorithmException ignored )
		{
		}
		return hash;
	}

	@Override
	public int hashCode ()
	{
		return getHash().hashCode();
	}

	@Override
	public boolean equals (Object obj)
	{
		return getHash().equals(((Transaction) obj).getHash());
	}

}
