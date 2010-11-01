package edu.uconn.vstlf.neuro.ekf;

import java.util.Vector;

import edu.uconn.vstlf.matrix.IncompatibleMatrixExpt;
import edu.uconn.vstlf.matrix.Matrix;

public class EKFANN {
	static private double hiddenInput = 1.0;
	static private double weightChange = 10E-8;
	
	private int[] layersShp_;
	
	/* contain all neurons in EKFANN.
	 * layer i has layerSize[i] neurons and
	 * each neuron has layerSize[i-1]+1 weights. 
	 * Thus layer i has (layerSize[i]*(layerSize[i-1]+1)) weights
	 */
	private Vector<EKFNeuron[]> neurons_;
	
	public EKFANN(int [] layersShape)
	{
		layersShp_ = layersShape;
		// Add input layer
		neurons_.add(new EKFNeuron[getLayerSize(0)]);
		for (int i = 0; i < getLayerSize(0); ++i)
			neurons_.lastElement()[i] = new EKFNeuron(null, null);
		
		// Add interior layers and output layers
		for (int il = 1; il < getNumLayers(); ++il) {
			double [] weights = new double[getNeuronWeightSize(il)];
			for (int j = 0; j < getNeuronWeightSize(il); ++j)
				weights[j] = Math.random();
			TransFunc func = (il == getNumLayers() - 1 ? new Identity() : new Tanh());
			
			neurons_.add(new EKFNeuron[getLayerSize(il)]);
			for (int i = 0; i < getLayerSize(il); ++i)
				neurons_.lastElement()[i] = new EKFNeuron(weights, func);
		}
	}
	
	public double[] getWeights()
	{
		int n = getWeightsSize();
		double[] w = new double[n];
		
		int index = 0;
		for (int l = 1; l < getNumLayers(); ++l) {
			EKFNeuron[] neurons = neurons_.elementAt(l);
			for (int i = 0; i < neurons.length; ++i) {
				double[] srcw = neurons[i].getWeights();
				System.arraycopy(srcw, 0, w, index, srcw.length);
				index += srcw.length;
			}
		}
		return w;
	}
	
	public void setWeights(double[] weights) throws Exception
	{
		if (weights.length != getWeightsSize())
			throw new Exception("the weights to be set is too large/small");
		
		int index = 0;
		for (int l = 1; l < getNumLayers(); ++l) {
			EKFNeuron[] neurons = neurons_.elementAt(l);
			int wlen = getNeuronWeightSize(l);
			for (int i = 0; i < neurons.length; ++i) {
				EKFNeuron neu = neurons[i];
				System.arraycopy(weights, index, neu.getWeights(), 0, wlen);
				index += wlen;
			}
		}
		
		if (index != getWeightsSize())
			throw new Exception("Error while setting weights");
	}
	
	public int getNumLayers() { return layersShp_.length; }
	public int getLayerSize(int layerIndex) { return layersShp_[layerIndex]; }
	
	private int getWeightsSize()
	{
		int n = 0;
		for (int l = 1; l < getNumLayers(); ++l)
			n += getLayerSize(l)*getNeuronWeightSize(l);
		return n;
	}
	
	private int getNeuronWeightSize(int layerIndex)
	{
		return getLayerSize(layerIndex-1)+1;
	}
	
	public double[] execute(double[] inputs) throws Exception
	{
		setInput(inputs);
		forwardPropagate();
		return getOutput();
	}
	
	public void setInput(double[] input) throws Exception
	{
		if (input.length != getLayerSize(0))
			throw new Exception("Input is not compatible with the EKF neural network");
		
		EKFNeuron[] inputLayer = neurons_.firstElement();
		for (int i = 0; i < input.length; ++i) {
			inputLayer[i].setWeightedSum(input[i]);
			inputLayer[i].setOutput(input[i]);
		}
	}
	
	public double [] getOutput() {
		double [] output = new double[getLayerSize(getNumLayers()-1)];
		EKFNeuron[] outputLayer = neurons_.lastElement();
		for (int i = 0; i < output.length; ++i)
			output[i] = outputLayer[i].getOutput();
		
		return output;
	}
	
	public void forwardPropagate() throws Exception
	{
		forwardPropagate(1, getNumLayers()-1);
	}
	
	public void forwardPropagate(int startLayer, int endLayer) throws Exception
	{
		if (startLayer < 1 || startLayer >= getNumLayers())
			throw new Exception("EKFANN cannot start forward propagation from layer " + startLayer);
		
		for (int li = startLayer; li <= endLayer; ++li) {
			EKFNeuron[] curLayer = neurons_.elementAt(li);
			EKFNeuron[] prevLayer = neurons_.elementAt(li - 1);
			// Get inputs from the previous layer
			double[] prevInput = new double[getLayerSize(li-1)+1];
			for (int pi = 0; pi < getLayerSize(li-1); ++pi)
				prevInput[pi] = prevLayer[pi].getOutput();
			prevInput[getLayerSize(li-1)] = hiddenInput;
			
			// Forward propagate
			for (int ni = 0; ni < getLayerSize(li); ++ni) {
				curLayer[ni].forwardPropagate(prevInput);
			}
		}
		
	}
	
	public void fowardPropagateWeightChange(int toNeuronLayer, int fromNeuronIndex, int toNeuronIndex, double weightChange) throws Exception
	{
		if (toNeuronLayer < 1 || toNeuronLayer >= getNumLayers())
			throw new Exception("Cannot change the weight in neuron at layer " + toNeuronLayer 
					+". EKF network has only " + getNumLayers() + " layers");
		
		EKFNeuron[] prevLayer = neurons_.elementAt(toNeuronLayer-1);
		EKFNeuron[] toLayer = neurons_.elementAt(toNeuronLayer);
		double fromInput = prevLayer[fromNeuronIndex].getOutput();
		EKFNeuron toNeuron = toLayer[toNeuronIndex];
		
		// Save changed data
		Vector<SavedNeuronData> savedata = new Vector<SavedNeuronData>();
		savedata.add(new SavedNeuronData(toNeuron));
		for (int l = toNeuronLayer+1; l < getNumLayers(); ++l) {
			EKFNeuron[] neurons = neurons_.elementAt(l);
			for (int n = 0; n < neurons.length; ++n)
				savedata.add(new SavedNeuronData(neurons[n]));
		}
		
		// incrementally compute the weighted sum and output after weight adjustment
		toNeuron.setWeightedSum(toNeuron.getWeightedSum() + weightChange*fromInput);
		toNeuron.setOutput(toNeuron.computeOutput(toNeuron.getWeightedSum()));
		
		// incrementally compute the weighted sum and output in the next layer
		int nextLayer = toNeuronLayer + 1;
		if (nextLayer < getNumLayers()) {
			double inputChange = toNeuron.getOutput() - savedata.firstElement().output;
			EKFNeuron[] neurons = neurons_.elementAt(nextLayer);
			for (int i = 0; i < neurons.length; ++i) {
				EKFNeuron neu = neurons[i];
				double weight = neu.getWeights()[toNeuronIndex];
				neu.setWeightedSum(neu.getWeightedSum() + inputChange*weight);
				neu.setOutput(neu.computeOutput(neu.getWeightedSum()));
			}
		}
		
		// forward propagate
		forwardPropagate(nextLayer + 1, getNumLayers()-1);
		
		// restore the outputs;
		for (int i = 0; i < savedata.size(); ++i) {
			SavedNeuronData snd = savedata.elementAt(i);
			EKFNeuron n = snd.neuron;
			n.setWeightedSum(snd.weightedSum);
			n.setOutput(snd.output);
		}
	}
	
	public void backwardPropagation(double[] inputs, double[] outputs, double[] weights, Matrix P) throws Exception
	{
		int wn = getWeightsSize();
		int outn = neurons_.lastElement().length;
		
		Matrix Q = new Matrix(wn, wn), R = new Matrix(outn, outn);
		
		setWeights(weights);
		double[] w_t_t1 = weights;
		Matrix P_t1_t1 = P;
		Matrix P_t_t1 = Matrix.copy(P_t1_t1);
		Matrix.add(P_t_t1, Q);
		// forward propagation;
		double[] z_t_t1 = execute(inputs);
		// get jacobian matrix
		Matrix H_t = jacobian();
		
		// compute S(t)
		Matrix S_t = new Matrix(outn, outn);
		Matrix S_temp = new Matrix(P_t_t1.getRow(), H_t.getRow());
		Matrix.multiply_trans2(P_t_t1, H_t, S_temp);
		Matrix.multiply(H_t, S_temp, S_t);
		Matrix.add(S_t, R);
		
		// compute K(t)
		Matrix S_t_inv = new Matrix(outn, outn);
		Matrix.inverse(Matrix.copy(S_t), S_t_inv);
		Matrix K_t = new Matrix(wn, outn);
		Matrix.multiply(S_temp, S_t_inv, K_t);
		
		// Compute w(t|t)
		double [] uz = new double[outn];
		for (int i = 0; i < outn; ++i)
			uz[i] = outputs[i] - z_t_t1[i];
		double [] w_t_t = new double[wn];
		Matrix.multiply(K_t, uz, w_t_t);
		for (int i = 0; i < wn; ++i)
			w_t_t[i] += w_t_t1[i];
		
		Matrix KHMult = new Matrix(wn, wn);
		Matrix.multiply(K_t, H_t, KHMult);
		// Compute I-K(t)*H(t)
		for (int i = 0; i < wn; ++i)
			for (int j = 0; j < wn; ++j)
				KHMult.setVal(i, j, (i == j ? 1.0 - KHMult.getVal(i, j) : -KHMult.getVal(i,j)));
		Matrix I_minus_KHMult = KHMult;
		
		// Compute P(t|t)
		Matrix KRMult = new Matrix(wn, outn);
		Matrix.multiply(K_t, R, KRMult);
		Matrix KRK_trans = new Matrix(wn, wn);
		Matrix.multiply_trans2(KRMult, K_t, KRK_trans);
		
		Matrix P_temp_mult = new Matrix(wn, wn);
		Matrix.multiply(I_minus_KHMult, P_t_t1, P_temp_mult);
		Matrix P_temp = new Matrix(wn, wn);
		Matrix.multiply_trans2(P_temp_mult, I_minus_KHMult, P_temp);
		
		for (int i = 0; i < wn; ++i)
			for (int j = 0; j < wn; ++j)
				P.setVal(i, j, (P_temp.getVal(i, j) + P_temp.getVal(j, i))/2.0);
		// copy back weights
		System.arraycopy(w_t_t, 0, weights, 0, wn);
	}
	
	Matrix jacobian() throws Exception
	{
		Matrix H_t = new Matrix(neurons_.lastElement().length, getWeightsSize());
		int hCol = 0;
		for (int l = 1; l < getNumLayers(); ++l){
			EKFNeuron[] neurons = neurons_.elementAt(l);
			for (int toNeuronIndex = 0; toNeuronIndex < neurons.length; ++toNeuronIndex) {
				EKFNeuron neu = neurons[toNeuronIndex];
				for (int fromNeuronIndex = 0; fromNeuronIndex < neu.getWeights().length; ++fromNeuronIndex) {
					// Compute a column of H(t)
					fowardPropagateWeightChange(l, fromNeuronIndex, toNeuronIndex, weightChange);
					double [] pout = getOutput();
					for (int k = 0; k < pout.length; ++k)
						H_t.setVal(k, hCol, pout[k]);
					++hCol;
				}
			}
		}
		return H_t;
	}
}

class SavedNeuronData
{
	SavedNeuronData(EKFNeuron neu)
	{
		neuron = neu;
		weightedSum = neu.getWeightedSum();
		output = neu.getOutput();
	}
	
	public EKFNeuron neuron;
	public double weightedSum, output;
}

abstract class TransFunc
{
	public abstract double compute(double v);
}

class Tanh extends TransFunc
{
	public double compute(double v) { return Math.tanh(v); }
}

class Identity extends TransFunc
{
	public double compute(double v) { return v; }
}

class EKFNeuron
{
	private double weightedSum_, output_;
	
	private double [] weights_;
	private TransFunc func_;
	public EKFNeuron(double[] weights, TransFunc func)
	{
		weights_ = weights;
		func_ = func;
	}
	
	double forwardPropagate(double[] inputs) throws Exception
	{
		if (inputs.length != weights_.length)
			throw new Exception("In EKFNeuron: too many/few inputs");
		
		weightedSum_ = computeWeightedSum(inputs);
		output_ = computeOutput(weightedSum_);
		return output_;
	}

	double[] getWeights() { return weights_; }
	
	double getWeightedSum() { return weightedSum_; }
	void setWeightedSum(double ws) { weightedSum_ = ws; }
	double computeWeightedSum(double[] inputs)
	{
		double weightedSum = 0.0;
		for (int i = 0; i < inputs.length; ++i) {
			weightedSum += inputs[i]*weights_[i];
		}
		return weightedSum;
	}
	
	double getOutput() { return output_; }
	void setOutput(double out) { output_ = out; }
	double computeOutput(double weightedSum) 
	{ return func_.compute(weightedSum); }
}