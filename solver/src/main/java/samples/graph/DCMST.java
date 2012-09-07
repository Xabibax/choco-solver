/**
 *  Copyright (c) 1999-2011, Ecole des Mines de Nantes
 *  All rights reserved.
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 *      * Redistributions of source code must retain the above copyright
 *        notice, this list of conditions and the following disclaimer.
 *      * Redistributions in binary form must reproduce the above copyright
 *        notice, this list of conditions and the following disclaimer in the
 *        documentation and/or other materials provided with the distribution.
 *      * Neither the name of the Ecole des Mines de Nantes nor the
 *        names of its contributors may be used to endorse or promote products
 *        derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND ANY
 *  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY
 *  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package samples.graph;

import gnu.trove.impl.hash.TIntIntHash;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import ilog.concert.*;
import ilog.cplex.*;
import choco.kernel.ESat;
import choco.kernel.ResolutionPolicy;
import choco.kernel.common.util.procedure.PairProcedure;
import solver.Solver;
import solver.constraints.Constraint;
import solver.constraints.gary.GraphConstraintFactory;
import solver.constraints.propagators.Propagator;
import solver.constraints.propagators.PropagatorPriority;
import solver.constraints.propagators.gary.basic.PropKCC;
import solver.constraints.propagators.gary.basic.PropMaxDiameter;
import solver.constraints.propagators.gary.basic.PropMaxDiameterFromNode;
import solver.constraints.propagators.gary.degree.PropAtLeastNNeighbors;
import solver.constraints.propagators.gary.degree.PropAtMostNNeighbors;
import solver.constraints.propagators.gary.flow.PropGCC_LowUp_undirected;
import solver.constraints.propagators.gary.flow.PropGCC_cost_LowUp_undirected;
import solver.constraints.propagators.gary.trees.PropTreeEvalObj;
import solver.constraints.propagators.gary.trees.PropTreeNoSubtour;
import solver.constraints.propagators.gary.trees.lagrangianRelaxation.*;
import solver.exception.ContradictionException;
import solver.objective.MinObjectiveManager;
import solver.objective.strategies.BottomUp_Minimization;
import solver.objective.strategies.Dichotomic_Minimization;
import solver.propagation.IPropagationEngine;
import solver.propagation.PropagationEngine;
import solver.propagation.generator.PArc;
import solver.propagation.generator.Sort;
import solver.recorders.fine.AbstractFineEventRecorder;
import solver.search.loop.monitors.SearchMonitorFactory;
import solver.search.loop.monitors.VoidSearchMonitor;
import solver.search.strategy.StrategyFactory;
import solver.search.strategy.decision.Decision;
import solver.search.strategy.strategy.AbstractStrategy;
import solver.search.strategy.strategy.StaticStrategiesSequencer;
import solver.search.strategy.strategy.graph.ArcStrategy;
import solver.search.strategy.strategy.graph.GraphStrategy;
import solver.variables.EventType;
import solver.variables.IntVar;
import solver.variables.Variable;
import solver.variables.VariableFactory;
import solver.variables.delta.IGraphDeltaMonitor;
import solver.variables.graph.GraphType;
import solver.variables.graph.INeighbors;
import solver.variables.graph.graphOperations.connectivity.ConnectivityFinder;
import solver.variables.graph.undirectedGraph.UndirectedGraph;
import solver.variables.graph.undirectedGraph.UndirectedGraphVar;
import java.io.*;
import java.util.ArrayList;

/**
 * Parse and solve an symmetric Traveling Salesman Problem instance of the TSPLIB
 */
public class DCMST {

	//***********************************************************************************
	// VARIABLES
	//***********************************************************************************

	//	private static int upperBound;
	// input
	private static String dir = "/Users/jfages07/Desktop/ConstrainedTrees/instances";
	private static String testalagPath = "/Users/jfages07/Desktop/ConstrainedTrees/archive/codeAlex";// path of testalag
	private static String instanceName;
	private static int n,nMin,nMax;
	private static int[] dMax;
	private static int[][] dist;
	// model
	private static IntVar totalCost;
	private static Solver solver;
	private static int search;
	private static int lb,ub;
	// other
	private static long TIMELIMIT = 600000;
	//	private static final long TIMELIMIT = 14400000;
	private static String outFile;
	private static PropTreeHeldKarp hk;
	private static boolean optGiven = false;

	//***********************************************************************************
	// METHODS
	//***********************************************************************************

	public static void main(String[] args) {
		//DE,DR,instanciasT
		bench("DE");
//		outFile = "DE"+"test"+search+".csv";
//		execute(dir,"DE",false,TIMELIMIT,outFile);
	}

	public static void bench(String type) {
		if(optGiven){
			search = 0;
		}else{
			search = 1;
		}
		HCP_Parser.clearFile(outFile = type+"botup_minCostTieBreak_s"+search+".csv");
		HCP_Parser.writeTextInto("instance;sols;fails;nodes;time;obj;lb;ub;search;\n", outFile);
		File folder = new File(dir+"/"+type);
		String[] list = folder.list();
		nMin = 100;
		nMax = 600;
		for (String s : list) {
			File file = new File(dir+"/"+type+"/"+s);
//			if(s.contains("600_3"))
			if(s.contains("300_2"))
			if((!file.isHidden()) && (!s.contains("bounds.csv")) && (!s.contains("bug"))){
				instanceName = s;
				System.out.println(s);
				if(parse(file)){
					if(optGiven){
						setUB(dir+"/"+type,s);
					}
					solveDCMST(s);
//					out(s);
//					System.exit(0);
				}
				System.gc();
			}
		}
	}

	public static void execute(String instanceDir, String type, boolean giveOpt, long tl, String out) {
		dir = instanceDir;
		outFile = out;
		optGiven = giveOpt;
		TIMELIMIT = tl;
		if(optGiven){
			search = 0;
		}else{
			search = 1;
		}
		HCP_Parser.clearFile(outFile);
		HCP_Parser.writeTextInto("instance;sols;fails;nodes;time;obj;lb;ub;search;\n", outFile);
		File folder = new File(dir+"/"+type);
		String[] list = folder.list();
		nMin = 100;
		nMax = 600;
		for (String s : list) {
			File file = new File(dir+"/"+type+"/"+s);
			if((!file.isHidden()) && (!s.contains("bounds.csv")) && (!s.contains("bug"))){
				instanceName = s;
				System.out.println(s);
				if(parse(file)){
					if(optGiven){
						setUB(dir+"/"+type,s);
					}
					solveDCMST(s);
				}
				System.gc();
			}
		}
	}



	private static void out(String s){
		HCP_Parser.clearFile(s);
		HCP_Parser.writeTextInto("1\n\n", s);
		final UndirectedGraph undi = new UndirectedGraph(n, GraphType.LINKED_LIST);
		for(int i=0;i<n;i++){
			for(int j=i+1;j<n;j++){
				if(dist[i][j]!=-1 && !(dMax[i]==1 && dMax[j]==1)){
					undi.addEdge(i,j);
				}
			}
		}
		int m = 0;
		for(int i=0;i<n;i++){
			m+=undi.getSuccessorsOf(i).neighborhoodSize();
		}
		m/=2;
		HCP_Parser.writeTextInto(n+"\t"+m+"\n", s);
		String deg = "";
		for(int i=0;i<n;i++){
			deg += dMax[i]+"\n";
		}
		HCP_Parser.writeTextInto(deg, s);
		for(int i=0;i<n;i++){
			INeighbors nei = undi.getSuccessorsOf(i);
			String neist = "";
			for(int j=nei.getFirstElement();j>=0;j=nei.getNextElement()){
				if(i<j)
				neist += (i+1)+"\t"+(j+1)+"\t"+dist[i][j]+"\n";
			}
			HCP_Parser.writeTextInto(neist, s);
		}
	}

	private static void setRCInput(UndirectedGraphVar g, String s){
		HCP_Parser.clearFile(s);
		HCP_Parser.writeTextInto("1\n\n", s);
		int m = 0;
		for(int i=0;i<n;i++){
			m+=g.getEnvelopGraph().getSuccessorsOf(i).neighborhoodSize();
		}
		m/=2;
		HCP_Parser.writeTextInto(n+"\t"+m+"\n", s);
		String deg = "";
		for(int i=0;i<n;i++){
			deg += dMax[i]+"\n";
		}
		HCP_Parser.writeTextInto(deg, s);
		for(int i=0;i<n;i++){
			INeighbors nei = g.getEnvelopGraph().getSuccessorsOf(i);
			String neist = "";
			for(int j=nei.getFirstElement();j>=0;j=nei.getNextElement()){
				if(i<j)
				neist += (i+1)+"\t"+(j+1)+"\t"+dist[i][j]+"\n";
			}
			HCP_Parser.writeTextInto(neist, s);
		}
	}

	private static double getRCOutput(String path){
		File file = new File(path+"/results.txt");
		try {
			BufferedReader buf = new BufferedReader(new FileReader(file));
			String line1 = buf.readLine();
			String line2 = buf.readLine();
			while(line2!=null){
				line1 = line2;
				line2 = buf.readLine();
			}
			// TODO
			String[] numbers = line1.split(" ");
			double bestLB  = Double.parseDouble(numbers[42]);
			return bestLB;
		} catch (Exception e) {
			throw new UnsupportedOperationException();
		}
	}

	private static class RCProp extends Propagator{
		final static String inputName = "RCinput";
		UndirectedGraphVar graph;
		IntVar obj;

		protected RCProp(UndirectedGraphVar graph, IntVar obj, Solver solver, Constraint constraint) {
			super(new Variable[]{graph,obj}, solver, constraint, PropagatorPriority.CUBIC, false);
			this.graph = graph;
			this.obj = obj;
		}

		@Override
		public int getPropagationConditions(int vIdx) {
			return EventType.REMOVEARC.mask+EventType.ENFORCEARC.mask;
		}

		@Override
		public void propagate(int evtmask) throws ContradictionException {
			// set input data
			setRCInput(graph,testalagPath+"/"+inputName);
			// execute RC
			String cmd = "."+testalagPath+"/testalag "+inputName+" 1 1 1000 30";
			try {
				Runtime r = Runtime.getRuntime();
				Process p = r.exec(cmd);
				p.waitFor();
			}catch(Exception e) {
				System.out.println("erreur d'execution " + cmd + e.toString());
				System.exit(0);
			}
			// get output data
			double lb = getRCOutput(testalagPath);
			obj.updateLowerBound((int)lb,this);
			if(lb-Math.floor(lb)>0.001){
				obj.updateLowerBound((int) Math.ceil(lb), this);
			}
		}

		@Override
		public void propagate(AbstractFineEventRecorder eventRecorder, int idxVarInProp, int mask) throws ContradictionException {
			forcePropagate(EventType.FULL_PROPAGATION);
		}

		@Override
		public ESat isEntailed() {
			return ESat.UNDEFINED;
		}
	}

	public static boolean parse(File file) {
		try {
			BufferedReader buf = new BufferedReader(new FileReader(file));
			String line = buf.readLine();
			String[] numbers;
			n = Integer.parseInt(line);
			if(n<nMin || n>nMax){
				return false;
			}
			dist = new int[n][n];
			dMax = new int[n];
			for(int i=0;i<n;i++){
				line = buf.readLine();
				numbers = line.split(" ");
				if(Integer.parseInt(numbers[0])!=i+1){
					throw new UnsupportedOperationException();
				}
				dMax[i] = Integer.parseInt(numbers[1]);
				for(int j=0;j<n;j++){
					dist[i][j] = -1;
				}
			}
			line = buf.readLine();
			int from,to,cost;
			int min = 1000000;
			int max = 0;
			while(line!=null){
				numbers = line.split(" ");
				from = Integer.parseInt(numbers[0])-1;
				to   = Integer.parseInt(numbers[1])-1;
				cost = Integer.parseInt(numbers[2]);
				min = Math.min(min, cost);
				max = Math.max(max, cost);
				if(dist[from][to]!=-1){
					throw new UnsupportedOperationException();
				}
				dist[from][to] = dist[to][from] = cost;
				line = buf.readLine();
			}
			lb = (n-1)*min;
			ub = (n-1)*max;
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
		throw new UnsupportedOperationException();
	}

	private static void setUB(String dir, String inst) {
		if(dir.contains("ham")){
			setHamUB(dir,inst);
			return;
		}
		File file = new File(dir+"/bounds.csv");
		try {
			BufferedReader buf = new BufferedReader(new FileReader(file));
			String line = buf.readLine();
			String[] numbers;
			line = buf.readLine();
			while(line!=null){
				numbers = line.split(";");
				if(n==Integer.parseInt(numbers[0])){
					if(inst.contains("0_1")){
						// nothing to do
					}else if(inst.contains("0_2")){
						line = buf.readLine();
						numbers = line.split(";");
					}else if(inst.contains("0_3")){
						line = buf.readLine();
						line = buf.readLine();
						numbers = line.split(";");
					}else if(inst.contains("0_4")){
						line = buf.readLine();
						line = buf.readLine();
						line = buf.readLine();
						numbers = line.split(";");
					}else if(inst.contains("0_5")){
						line = buf.readLine();
						line = buf.readLine();
						line = buf.readLine();
						line = buf.readLine();
						numbers = line.split(";");
					}else{
						throw new UnsupportedOperationException(inst);
					}
					ub = Integer.parseInt(numbers[2]);
					System.out.println("ub : "+ub);
					return;
				}
				line = buf.readLine();
			}
			System.out.println("no bound");
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	private static void setHamUB(String dir, String inst) {
		File file = new File(dir+"/bounds.csv");
		try {
			BufferedReader buf = new BufferedReader(new FileReader(file));
			String line = buf.readLine();
			String[] numbers;
			line = buf.readLine();
			while(line!=null){
				numbers = line.split(";");
				if(n==Integer.parseInt(numbers[0])){
					if(inst.contains("0_0")){
						// nothing to do
					}else if(inst.contains("0_1")){
						line = buf.readLine();
						numbers = line.split(";");
					}else if(inst.contains("0_2")){
						line = buf.readLine();
						line = buf.readLine();
						numbers = line.split(";");
					}else{
						throw new UnsupportedOperationException(inst);
					}
					ub = Integer.parseInt(numbers[2]);
					System.out.println("ub : "+ub);
					return;
				}
				line = buf.readLine();
			}
			System.out.println("no bound");
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	private static void solveDCMST(String instanceName) {
		solver = new Solver();

//		int mc = ub;
//		for(int i=0;i<n;i++){
//			for(int j=i+1;j<n;j++){
//				if(dist[i][j]!=-1 && mc > dist[i][j]){
//					mc = dist[i][j];
//				}
//			}
//		}
//		for(int i=0;i<n;i++){
//			for(int j=i+1;j<n;j++){
//				if(dist[i][j]!=-1){
//					dist[i][j]-=mc;
//				}
//			}
//		}
//		ub-=(n-1)*mc;

		// variables
//		lb = ub;
		totalCost = VariableFactory.bounded("obj",lb,ub,solver);
		final UndirectedGraphVar undi = new UndirectedGraphVar(solver, n, GraphType.ENVELOPE_SWAP_ARRAY, GraphType.LINKED_LIST);
		for(int i=0;i<n;i++){
			undi.getKernelGraph().activateNode(i);
			for(int j=i+1;j<n;j++){
				if(dist[i][j]!=-1 && !(dMax[i]==1 && dMax[j]==1)){
					undi.getEnvelopGraph().addEdge(i,j);
				}
			}
		}
		// constraints
		Constraint gc = GraphConstraintFactory.makeConstraint(solver);
		gc.addPropagators(new PropAtLeastNNeighbors(undi, 1, gc, solver));
		gc.addPropagators(new PropAtMostNNeighbors(undi, dMax, gc, solver));
		gc.addPropagators(new PropTreeNoSubtour(undi, gc, solver));
		gc.addPropagators(new PropKCC(undi, solver, gc, VariableFactory.bounded("1",1,1,solver)));
		gc.addPropagators(new PropTreeEvalObj(undi, totalCost, dist, gc, solver));
//		gc.addPropagators(new PropMaxDiameterFromNode(undi, 4, 0, gc, solver));

//		Propagator euclFilter = new Propagator<UndirectedGraphVar>(new UndirectedGraphVar[]{undi},solver,gc, PropagatorPriority.LINEAR) {
//
//			IGraphDeltaMonitor gdm = undi.monitorDelta(this);
//			PairProcedure proc = new PairProcedure() {
//				@Override
//				public void execute(int i, int j) throws ContradictionException {
//					check(i,j);
//				}
//			};
//
//			@Override
//			public int getPropagationConditions(int vIdx) {
//				return EventType.ENFORCEARC.mask;
//			}
//
//			@Override
//			public void propagate(int evtmask) throws ContradictionException {
//				for(int i=0;i<n;i++){
//					for(int j=i+1;j<n;j++){
//						if(undi.getKernelGraph().edgeExists(i,j)){
//							check(i,j);
//						}
//					}
//				}
//				gdm.unfreeze();
//			}
//
//			private void check(int a, int b) throws ContradictionException {
//				INeighbors anei = undi.getEnvelopGraph().getSuccessorsOf(a);
//				INeighbors bnei = undi.getEnvelopGraph().getSuccessorsOf(b);
//				for(int c=anei.getFirstElement();c>=0;c=anei.getNextElement()){
//					if(c!=a && c!=b)
//						if(undi.getEnvelopGraph().getSuccessorsOf(c).contain(b))
//							for(int d=bnei.getFirstElement();d>=0;d=bnei.getNextElement()){
//								if(c<d && d!=a && d!=b){
//									if(undi.getEnvelopGraph().getSuccessorsOf(d).contain(a))
//										if(dist[a][b]+dist[c][d]>dist[a][c]+dist[d][b]){
//											if(dist[a][b]+dist[c][d]>dist[a][d]+dist[c][b]){
//												undi.removeArc(c,d,this);
//											}
//										}
//								}
//							}
//				}
//			}
//
//			@Override
//			public void propagate(AbstractFineEventRecorder eventRecorder, int idxVarInProp, int mask) throws ContradictionException {
//				gdm.freeze();
//				gdm.forEachArc(proc,EventType.ENFORCEARC);
//				gdm.unfreeze();
//			}
//
//			@Override
//			public ESat isEntailed() {
//				return ESat.UNDEFINED;
//			}
//		};
//		Propagator lp2Filter = new Propagator<UndirectedGraphVar>(new UndirectedGraphVar[]{undi},solver,gc, PropagatorPriority.LINEAR) {
//
//			@Override
//			public int getPropagationConditions(int vIdx) {
//				return EventType.ENFORCEARC.mask+EventType.REMOVEARC.mask;
//			}
//
//			ArrayList<TIntArrayList> cuts,secs;
//			TIntIntHashMap map;
//			int[] dm;
//			UndirectedGraph gsol;
//			TIntArrayList[] list;
//			TIntArrayList ff,ft,ef,et;
//
//			@Override
//			public void propagate(int evtmask) throws ContradictionException {
//				if(cuts==null){
//					cuts = new ArrayList<TIntArrayList>();
//					secs = new ArrayList<TIntArrayList>();
//					map = new TIntIntHashMap();
//					dm = new int[n];
//					gsol = new UndirectedGraph(n,GraphType.LINKED_LIST);
//					list = new TIntArrayList[n];
//					ff = new TIntArrayList();
//					ft = new TIntArrayList();
//					ef = new TIntArrayList();
//					et = new TIntArrayList();
//				}
//				ff.clear();
//				ft.clear();
//				ef.clear();
//				et.clear();
//				cuts.clear();
//				secs.clear();
//				while(solveLP()){
////					System.exit(0);
//				}
//				int s = ff.size();
//				for(int i=0;i<s;i++){
//					undi.removeArc(ff.get(i),ft.get(i),this);
//				}
//				s = ef.size();
//				for(int i=0;i<s;i++){
//					undi.enforceArc(ef.get(i),et.get(i),this);
//				}
////				System.exit(0);
//			}
//
//			public boolean solveLP() throws ContradictionException {
//				try {
//					IloCplex cplex = new IloCplex();
//					cplex.setOut(new OutputStream() {public void write(int i) throws IOException {}});
//					int nbX = 0;
//					int nbK = 0;
//					INeighbors nei;
//					for(int i=0;i<n;i++){
//						nbK += undi.getKernelGraph().getSuccessorsOf(i).neighborhoodSize();
//						nbX += undi.getEnvelopGraph().getSuccessorsOf(i).neighborhoodSize();
//					}
//					nbX /= 2;
//					nbK /= 2;
//					nbX -= nbK;
//					double offObj = 0;
//					int[] costX = new int[nbX];
//					map.clear();
//					int[] revmap = new int[nbX];
//					int index = 0;
//					for(int i=0;i<n;i++){
//						gsol.getNeighborsOf(i).clear();
//					}
//					for(int i=0;i<n;i++){
//						dm[i] = dMax[i] - undi.getKernelGraph().getSuccessorsOf(i).neighborhoodSize();
//						nei = undi.getEnvelopGraph().getSuccessorsOf(i);
//						for(int j=nei.getFirstElement();j>=0;j=nei.getNextElement()){
//							if(i<j){
//								if(!undi.getKernelGraph().arcExists(i,j)){
//									map.put(i*n+j,index);
//									revmap[index] = i*n+j;
//									costX[index] = dist[i][j];
//									index++;
//								}else{
//									offObj += dist[i][j];
//									gsol.addEdge(i,j);
//								}
//							}
//						}
//					}
//					IloNumVar[] x = cplex.numVarArray(nbX,0,1);
////					IloNumVar obj = cplex.numVar(totalCost.getLB(),totalCost.getUB());
////					cplex.addEq(cplex.scalProd(x,costX),obj);
//					//obj
////					cplex.addMinimize(obj);
//					cplex.addMinimize(cplex.scalProd(x,costX));
//					//cons
//					cplex.addEq(cplex.sum(x),n-1-nbK);
//					for(int i=0;i<n;i++){
//						if(undi.getEnvelopGraph().getSuccessorsOf(i).neighborhoodSize()>undi.getKernelGraph().getSuccessorsOf(i).neighborhoodSize()){
//							list[i] = new TIntArrayList();
//						}else{
//							list[i] = null;
//						}
//					}
//					index = 0;
//					for(int i=0;i<n;i++){
//						nei = undi.getEnvelopGraph().getSuccessorsOf(i);
//						if(nei.neighborhoodSize()>undi.getKernelGraph().getSuccessorsOf(i).neighborhoodSize()){
//							for(int j=nei.getFirstElement();j>=0;j=nei.getNextElement()){
//								if(i<j){
//									if(!undi.getKernelGraph().arcExists(i,j)){
//										if(!list[i].contains(index)){
//											list[i].add(index);
//										}
//										if(!list[j].contains(index)){
//											list[j].add(index);
//										}
//										index++;
//									}
//								}
//							}
//						}
//					}
//					for(int i=0;i<n;i++){
//						if(list[i]!=null){
//							IloNumVar[] arr = new IloNumVar[list[i].size()];
//							for(int k=list[i].size()-1;k>=0;k--){
//								arr[k] = x[list[i].get(k)];
//							}
//							cplex.addLe(cplex.sum(arr), dm[i]);
//							if(undi.getKernelGraph().getSuccessorsOf(i).neighborhoodSize()==0){
//								cplex.addGe(cplex.sum(arr), 1);
//							}
//						}
//					}
//					//cons
//					//subtours
//					for(int s=0;s<cuts.size();s++){
//						int k = cuts.get(s).size();
//						IloNumVar[] newcut = new IloNumVar[k];
//						for(int i=0;i<k;i++){
//							newcut[i] = x[cuts.get(s).get(i)];
//						}
//						cplex.addGe(cplex.sum(newcut),1);
//					}
//					for(int s=0;s<secs.size();s++){
//						int k = secs.get(s).size();
//						IloNumVar[] newcut = new IloNumVar[k];
//						for(int i=0;i<k;i++){
//							newcut[i] = x[secs.get(s).get(i)];
//						}
//						cplex.addLe(cplex.sum(newcut),k-1);
////						System.out.println(secs.get(s)+" <= "+(k-1));
//					}
//					//solve
////					cplex.exportModel("/Users/jfages07/Desktop/truc.mps");
//					boolean again = false;
//					if ( cplex.solve() ) {
////						System.out.println("Solution status = " + cplex.getStatus());
////						System.out.println("Solution value = " + cplex.getObjValue());
//						// FILTERING
//						double no = cplex.getObjValue()+offObj;
////						System.out.println(no);
//						if(no>totalCost.getLB()){
////							System.out.println("IMPROVED to "+no);
//						}
//						totalCost.updateLowerBound((int)Math.ceil(no),this);
//						for(int i=0;i<nbX;i++){
//							if(cplex.getValue(x[i])>0){
//								int from = revmap[i]/n;
//								int to = revmap[i]%n;
//								gsol.addEdge(from,to);
//							}
//							if(cplex.getBasisStatus(x[i]).toString().equals("AtUpper")){
//								if(no+cplex.getReducedCost(x[i])>ub){
//									int from = revmap[i]/n;
//									int to = revmap[i]%n;
//									ef.add(from);
//									et.add(to);
//								}
//							}else if(cplex.getBasisStatus(x[i]).toString().equals("AtLower")){
//								if(no+cplex.getReducedCost(x[i])>ub){
//									int from = revmap[i]/n;
//									int to = revmap[i]%n;
//									ff.add(from);
//									ft.add(to);
//								}
//							}
//						}
//						// triangle
//						for(int i=0;i<n;i++){
//							for(int j=i+1;j<n;j++){
//								if(map.containsKey(i*n+j))
//									for(int k=j+1;k<n;k++){
//										if(map.containsKey(i*n+k))
//											if(map.containsKey(j*n+k)){
//												int a = map.get(i*n+j);
//												int b = map.get(j*n+k);
//												int c = map.get(i*n+k);
//												if(cplex.getValue(x[a])+cplex.getValue(x[b])+cplex.getValue(x[c])>2){
//													secs.add(new TIntArrayList(new int[]{a,b,c}));
//													again = true;
//												}
//											}
//
//
//									}
//							}
//						}
//						// subtour
////						int[] subtour = findsubtour(cplex.getValues(x));
////						if(subtour.length>1){
////							TIntArrayList tour = new TIntArrayList();
////							int v;
////							if(subtour.length>2){
////								System.out.println(subtour[0]+"-"+subtour[subtour.length-1]);
////								System.out.println(map.containsKey(subtour[0]*n+subtour[subtour.length-1]));
////								if(subtour[0]<subtour[subtour.length-1]){
////									v = map.get(subtour[0]*n+subtour[subtour.length-1]);
////								}else{
////									v = map.get(subtour[subtour.length-1]*n+subtour[0]);
////								}
////								tour.add(v);
////							}
////							for(int i=0;i<subtour.length-1;i++){
////								System.out.println(subtour[i]+"-"+subtour[i+1]);
////								if(subtour[i]<subtour[i+1]){
////									v = map.get(subtour[i]*n+subtour[i+1]);
////								}else{
////									v = map.get(subtour[i+1]*n+subtour[i]);
////								}
////								tour.add(v);
////							}
////							secs.add(tour);
////							System.out.println(tour);
////							System.exit(0);
////						}
////						System.exit(0);
//						// cut set
//						ConnectivityFinder ccf = new ConnectivityFinder(gsol);
//						ccf.findAllCC();
//						int nbcc = ccf.getNBCC();
//						if(nbcc>1){
//							for(int cc=0;cc<nbcc;cc++){
//								TIntArrayList cut = new TIntArrayList();
//								TIntArrayList nodesCC = new TIntArrayList();
//								for(int i=0;i<nbX;i++){
//									int from = revmap[i]/n;
//									int to = revmap[i]%n;
//									if((ccf.getNode_CC()[from]==cc && ccf.getNode_CC()[to]!=cc)
//											||(ccf.getNode_CC()[to]==cc && ccf.getNode_CC()[from]!=cc)){
//										if(cplex.getValue(x[i])>0){
//											throw new UnsupportedOperationException();
//										}
//										cut.add(i);
//										if(ccf.getNode_CC()[from]==cc && !nodesCC.contains(from)){
//											nodesCC.add(from);
//										}
//										if(ccf.getNode_CC()[to]==cc && !nodesCC.contains(to)){
//											nodesCC.add(to);
//										}
//									}
//								}
//								cuts.add(cut);
//								again = true;
//							}
//						}else{
////							System.out.println("NO SUBTOUR FOUND");
//						}
//					}else{
////						System.out.println(cplex.getStatus());
//						if(cplex.getStatus().toString().contains("Infeasible")){
//							contradiction(undi,"");
//							return false;
//						}
//						throw new UnsupportedOperationException();
//					}
//					cplex.end();
//					return again;
//					// create model and solve it
//				} catch (IloException e) {
//					System.err.println("Concert exception caught: " + e);
//				}
//				return false;
//			}
//
//			protected int[] findsubtour(double[] solarray){
//				boolean[] seen = new boolean[n];
//				int[] tour = new int[n];
//				int i, index, node;
//
//				for (i = 0; i < n; i++)
//					seen[i] = false;
//
//				node = 0;
//				while(undi.getEnvelopGraph().getSuccessorsOf(node).neighborhoodSize()==
//						undi.getKernelGraph().getSuccessorsOf(node).neighborhoodSize()){
//					node++;
//				}
//				for (index = 0; index < n; index++) {
//					tour[index] = node;
//					seen[node] = true;
//					INeighbors nei = undi.getEnvelopGraph().getSuccessorsOf(node);
//					for (i=nei.getFirstElement();i>=0;i=nei.getNextElement()){
//						if (node<i && map.containsKey(node * n + i) && solarray[map.get(node*n+i)] > 0.5 && !seen[i]) {
//							node = i;
//							break;
//						}
//						else if (node>i && map.containsKey(i*n+node) && solarray[map.get(i*n+node)] > 0.5 && !seen[i]) {
//							node = i;
//							break;
//						}
//					}
//					if (i == -1){
////						System.out.println("break");
//						break;
//					}
//				}
////				System.out.println(index+" ; "+n);
//				int result[] = new int[index+1];
//				for (i = 0; i <= index; i++){
//					result[i] = tour[i];
//					System.out.println(result[i]);
//				}
//				return result;
//			}
//
//			@Override
//			public void propagate(AbstractFineEventRecorder eventRecorder, int idxVarInProp, int mask) throws ContradictionException {
//				propagate(0);
//			}
//
//			@Override
//			public ESat isEntailed() {
//				return ESat.UNDEFINED;
//			}
//		};

		hk = PropTreeHeldKarp.mstBasedRelaxation(undi, totalCost, dMax, dist, gc, solver);
		hk.waitFirstSolution(!optGiven);
		gc.addPropagators(hk);

		// GCC
		int[] low = new int[n*2];
		int[] up = new int[n*2];
		int[][] costMatrix = new int[n*2][n*2];
		for(int i=0;i<n;i++){
			low[i] = up[i] = 1;
			low[i+n] = 0;
			up[i+n] = dMax[i]-1;
			INeighbors nei = undi.getEnvelopGraph().getSuccessorsOf(i);
			for(int j=nei.getFirstElement();j>=0;j=nei.getNextElement()){
				costMatrix[i][j+n] = costMatrix[j+n][i] = costMatrix[i+n][j] = costMatrix[j][i+n] = dist[i][j];
			}
		}
		low[0] = up[0] = 0;
		low[n] = 1;
		up[n] = dMax[0];
		IntVar flow = VariableFactory.bounded("flowMax",n-1,n-1,solver);
		gc.addPropagators(new PropGCC_LowUp_undirected(undi, flow, low, up, gc, solver));
		// cost-GCC
//		gc.addPropagators(new PropGCC_cost_LowUp_undirected(undi, flow, totalCost,
//		costMatrix,low, up, gc, solver));


//		gc.addPropagators(new RCProp(undi,totalCost,solver,gc));

//		gc.addPropagators(ProplittleBITreeHeldKarp.mstBasedRelaxation(undi, totalCost, dMax, dist, gc, solver));

//		gc.addPropagators(euclFilter);
//		gc.addPropagators(lp2Filter);
//		gc.addPropagators(PropBundleTreeHeldKarp.mstBasedRelaxation(undi, totalCost, dMax, dist, gc, solver));
//		gc.addPropagators(PropResetTreeHeldKarp.mstBasedRelaxation(undi, totalCost, dMax, dist, gc, solver));
//		gc.addPropagators(PropPositiveTreeHeldKarp.mstBasedRelaxation(undi, totalCost, dMax, dist, gc, solver));
//		gc.addPropagators(PropTreeHeldKarp.mstBasedRelaxation(undi, totalCost, dMax, dist, gc, solver));

		solver.post(gc);

		solver.getSearchLoop().plugSearchMonitor(new VoidSearchMonitor(){
			public void afterInitialPropagation() {
				int narc = 0;
				int nkarc = 0;
				int maxD = 0;
				for(int i=0;i<n;i++){
					narc += undi.getEnvelopGraph().getSuccessorsOf(i).neighborhoodSize();
					if(maxD < undi.getEnvelopGraph().getSuccessorsOf(i).neighborhoodSize()){
						maxD = undi.getEnvelopGraph().getSuccessorsOf(i).neighborhoodSize();
					}
					nkarc+= undi.getKernelGraph().getSuccessorsOf(i).neighborhoodSize();
				}
				narc /= 2;
				nkarc/= 2;
				System.out.println("%%%%%%%%%%%");
				System.out.println("M : "+narc+" / "+nkarc+"            "+(int)(solver.getMeasures().getInitialPropagationTimeCount()/1000)+"s");
				System.out.println("%%%%%%%%%%%");
				System.out.println(totalCost);
				System.out.println("%%%%%%%%%%%");
				System.out.println("max degree = "+maxD);
//				solver.getSearchLoop().interrupt();
//				setRCInput(undi,"filtered200");
//				System.exit(0);
			}
//			public void afterOpenNode(){
//				System.out.println(totalCost);
//			}
		});

//		config
		AbstractStrategy firstSol = StrategyFactory.graphStrategy(undi,null,new FirstSol(undi), GraphStrategy.NodeArcPriority.ARCS);
//		AbstractStrategy findOpt = StrategyFactory.graphStrategy(undi, null, new MST_MinDeg(undi), GraphStrategy.NodeArcPriority.ARCS);
//		findOpt = StrategyFactory.graphStrategy(undi, null,new GraphStrategyBench(undi,dist,hk,1,false), GraphStrategy.NodeArcPriority.ARCS);
//		findOpt = StrategyFactory.graphStrategy(undi,null,new viol(undi), GraphStrategy.NodeArcPriority.ARCS);
//		findOpt = StrategyFactory.graphStrategy(undi,null,new nextSol(undi), GraphStrategy.NodeArcPriority.ARCS);
//		firstSol = StrategyFactory.graphStrategy(undi, null,new GraphStrategyBench(undi,dist,hk,0,false), GraphStrategy.NodeArcPriority.ARCS);
		GraphStrategyBench2 gs = new GraphStrategyBench2(undi,dist,hk);
		gs.configure(3,true,true,false);
//		firstSol = gs;
//		AbstractStrategy strat = new Change(undi,firstSol,findOpt);
		AbstractStrategy strat = new Change(undi,firstSol,gs);
//		AbstractStrategy strat = firstSol;//= new Change(undi,firstSol,findOpt);
//		strat = StrategyFactory.graphRandom(undi,0);
//		strat = StrategyFactory.graphStrategy(undi, null,new GraphStrategyBench(undi,dist,null,4,true), GraphStrategy.NodeArcPriority.ARCS);
//		strat = StrategyFactory.graphStrategy(undi, null,new GraphStrategyBench(undi,dist,hk,0,true), GraphStrategy.NodeArcPriority.ARCS);
//		strat = firstSol;
//		strat = findOpt;
//		strat = StrategyFactory.graphRandom(undi,0);
//				strat = StrategyFactory.graphLexico(undi);

		switch (search){
			//ANDINST : first (if fail<100) then strat 0 truetrick
			//RANDOM :
//			case 0: solver.set(findOpt);break;
			case 0:
				solver.set(strat);
//				solver.getSearchLoop().restartAfterEachSolution(true);
				break;
			case 1: solver.set(new StaticStrategiesSequencer(new BottomUp_Minimization(totalCost),strat));break;
			case 2: solver.set(new StaticStrategiesSequencer(new Dichotomic_Minimization(totalCost,solver),strat));break;
			default: throw new UnsupportedOperationException();
		}
		IPropagationEngine propagationEngine = new PropagationEngine(solver.getEnvironment());
		solver.set(propagationEngine.set(new Sort(new PArc(propagationEngine, gc)).clearOut()));
		solver.getSearchLoop().getLimitsBox().setTimeLimit(TIMELIMIT);
		SearchMonitorFactory.log(solver, true, false);
		// resolution
		solver.findOptimalSolution(ResolutionPolicy.MINIMIZE, totalCost);
		if(solver.getMeasures().getSolutionCount()==0 && solver.getMeasures().getTimeCount()<TIMELIMIT){
//			throw new UnsupportedOperationException();
		}
		if(solver.getMeasures().getSolutionCount()>1 && optGiven){
//			throw new UnsupportedOperationException();
		}
		//output
		MinObjectiveManager man = (MinObjectiveManager)solver.getSearchLoop().getObjectivemanager();
		int bestLB = man.getBestKnownLowerBound();
		int bestUB = man.getBestKnownUpperBound();
		int bestCost = solver.getSearchLoop().getObjectivemanager().getBestValue();
		String txt = instanceName + ";" + solver.getMeasures().getSolutionCount() + ";" + solver.getMeasures().getFailCount() + ";"
				+ solver.getMeasures().getNodeCount() + ";"+ (int)(solver.getMeasures().getTimeCount()) + ";" + bestCost +";"+bestLB+";"+bestUB+";"+search+";\n";
		HCP_Parser.writeTextInto(txt, outFile);
	}

	private static class MST_MinDeg extends ArcStrategy<UndirectedGraphVar>{

		public MST_MinDeg (UndirectedGraphVar g){
			super(g);
		}

		@Override
		public boolean computeNextArc() {
			from = -1;
			to = -1;
			original();
			if(from==-1){
				return false;
			}
			return true;
		}

		private void minDelta(){
			INeighbors nei;
			int minDelta = 5*n;
			for(int i=0;i<n;i++){
				nei = hk.getMST().getSuccessorsOf(i);
				int k = g.getKernelGraph().getNeighborsOf(i).neighborhoodSize();
				int e = g.getEnvelopGraph().getNeighborsOf(i).neighborhoodSize();
				if(e!=k && e>dMax[i])
					for(int j=nei.getFirstElement();j>=0;j=nei.getNextElement()){
						if(!g.getKernelGraph().arcExists(i,j)){
							if(dMax[i]-k<minDelta){
								minDelta = dMax[i]-k;
							}
						}
					}
			}
			int minDeg = 3*n;
			for(int i=0;i<n;i++){
				nei = hk.getMST().getSuccessorsOf(i);
				int k = g.getKernelGraph().getNeighborsOf(i).neighborhoodSize();
				if(g.getEnvelopGraph().getNeighborsOf(i).neighborhoodSize()!=k && dMax[i]-k==minDelta)
					for(int j=nei.getFirstElement();j>=0;j=nei.getNextElement()){
						if(!g.getKernelGraph().arcExists(i,j)){
							int d = g.getEnvelopGraph().getNeighborsOf(i).neighborhoodSize()
									+ g.getEnvelopGraph().getNeighborsOf(j).neighborhoodSize();
							if(d<minDeg){
								minDeg = d;
								from = i;
								to = j;
							}
						}
					}
			}
			if(to!=-1){
				return;
			}
			for(int i=0;i<n;i++){
				nei = hk.getMST().getSuccessorsOf(i);
				int k = g.getKernelGraph().getNeighborsOf(i).neighborhoodSize();
				int e = g.getEnvelopGraph().getNeighborsOf(i).neighborhoodSize();
				if(e!=k)
					for(int j=nei.getFirstElement();j>=0;j=nei.getNextElement()){
						if(!g.getKernelGraph().arcExists(i,j)){
							if(dMax[i]-k<minDelta){
								minDelta = dMax[i]-k;
							}
						}
					}
			}
			for(int i=0;i<n;i++){
				nei = hk.getMST().getSuccessorsOf(i);
				int k = g.getKernelGraph().getNeighborsOf(i).neighborhoodSize();
				if(g.getEnvelopGraph().getNeighborsOf(i).neighborhoodSize()!=k && dMax[i]-k==minDelta)
					for(int j=nei.getFirstElement();j>=0;j=nei.getNextElement()){
						if(!g.getKernelGraph().arcExists(i,j)){
							int d = g.getEnvelopGraph().getNeighborsOf(i).neighborhoodSize()
									+ g.getEnvelopGraph().getNeighborsOf(j).neighborhoodSize();
							if(d<minDeg){
								minDeg = d;
								from = i;
								to = j;
							}
						}
					}
			}
		}

		private void minDelta2(){
			INeighbors nei;
			int minDelta = 5*n;
			int maxDeg = 0;
			from = -1;
			for(int i=0;i<n;i++){
				int k = g.getKernelGraph().getNeighborsOf(i).neighborhoodSize();
				int e = g.getEnvelopGraph().getNeighborsOf(i).neighborhoodSize();
				if(e!=k && e>dMax[i] && e>maxDeg){
					maxDeg = e;
					from = e;
				}
			}
			if(from==-1){
				for(int i=0;i<n;i++){
					int k = g.getKernelGraph().getNeighborsOf(i).neighborhoodSize();
					int e = g.getEnvelopGraph().getNeighborsOf(i).neighborhoodSize();
					if(e!=k && e>maxDeg){
						maxDeg = e;
						from = e;
					}
				}
			}
			if(from==-1){
				return;
			}
			int minDeg = 3*n;
			nei = g.getEnvelopGraph().getSuccessorsOf(from);
//			nei = hk.getMST().getSuccessorsOf(i);
			int k = g.getKernelGraph().getNeighborsOf(from).neighborhoodSize();
			for(int j=nei.getFirstElement();j>=0;j=nei.getNextElement()){
				if(!g.getKernelGraph().arcExists(from,j)){
					int d = g.getEnvelopGraph().getNeighborsOf(j).neighborhoodSize();
					if(d<minDeg){
						minDeg = d;
						to = j;
					}
				}
			}
			if(to==-1){
				System.out.println("g");
				System.out.println(nei);
				System.out.println(g.getKernelGraph().getNeighborsOf(from));
				System.exit(0);
			}
		}

		private void original(){
			INeighbors nei;
			int minDelta = 5*n;
			for(int i=0;i<n;i++){
				nei = hk.getMST().getSuccessorsOf(i);
				int k = g.getKernelGraph().getNeighborsOf(i).neighborhoodSize();
				int e = g.getEnvelopGraph().getNeighborsOf(i).neighborhoodSize();
				if(e!=k)
					for(int j=nei.getFirstElement();j>=0;j=nei.getNextElement()){
						if(!g.getKernelGraph().arcExists(i,j)){
							if(dMax[i]-k<minDelta){
								minDelta = dMax[i]-k;
							}
						}
					}
			}
			int minDeg = 0;//3*n;
			for(int i=0;i<n;i++){
				nei = hk.getMST().getSuccessorsOf(i);
				int k = g.getKernelGraph().getNeighborsOf(i).neighborhoodSize();
				if(g.getEnvelopGraph().getNeighborsOf(i).neighborhoodSize()!=k && dMax[i]-k==minDelta)
					for(int j=nei.getFirstElement();j>=0;j=nei.getNextElement()){
						if(!g.getKernelGraph().arcExists(i,j)){
							int d = g.getEnvelopGraph().getNeighborsOf(i).neighborhoodSize()
									+ g.getEnvelopGraph().getNeighborsOf(j).neighborhoodSize();
							if(d>minDeg){
								minDeg = d;
								from = i;
								to = j;
							}
						}
					}
			}
		}

	}

	private static class FirstSol extends ArcStrategy<UndirectedGraphVar>{

		public FirstSol (UndirectedGraphVar g){
			super(g);
		}

		@Override
		public boolean computeNextArc() {
			return computeNextArcOld();
		}

		public boolean computeNextArcNew() {
			from = -1;
			to = -1;
			int minCost = 0;
			INeighbors env,ker;
			//new
			for(int i=0;i<n;i++){
				ker = g.getKernelGraph().getSuccessorsOf(i);
				env = g.getEnvelopGraph().getSuccessorsOf(i);
				if(ker.neighborhoodSize()==0){
					for(int j=env.getFirstElement();j>=0;j=env.getNextElement()){
						int cost = dist[i][j];
//						if(g.getKernelGraph().getSuccessorsOf(j).neighborhoodSize()<dMax[j]-1)
						if(to==-1 || cost<minCost
						|| (cost<minCost && g.getKernelGraph().getSuccessorsOf(j).neighborhoodSize()<dMax[j]-1)){
							minCost = cost;
							from = i;
							to = j;
						}
					}
				}
			}
			for(int i=0;i<n;i++){
				ker = g.getKernelGraph().getSuccessorsOf(i);
				env = g.getEnvelopGraph().getSuccessorsOf(i);
				if(ker.neighborhoodSize()<env.neighborhoodSize()
					&& ker.neighborhoodSize()<dMax[i]-1){
					for(int j=env.getFirstElement();j>=0;j=env.getNextElement()){
						int cost = dist[i][j];
//						if(g.getKernelGraph().getSuccessorsOf(j).neighborhoodSize()<dMax[j]-1)
						if(to==-1 || cost<minCost
						|| (cost<minCost && g.getKernelGraph().getSuccessorsOf(j).neighborhoodSize()<dMax[j]-1)){
							minCost = cost;
							from = i;
							to = j;
						}
					}
				}
			}
//			if(to!=-1)
//				return true;
//			for(int i=0;i<n;i++){
//				ker = g.getKernelGraph().getSuccessorsOf(i);
//				env = g.getEnvelopGraph().getSuccessorsOf(i);
//				if(ker.neighborhoodSize()==0){
//					for(int j=env.getFirstElement();j>=0;j=env.getNextElement()){
//						int cost = dist[i][j];
//						if(to==-1 || cost<minCost){
//							minCost = cost;
//							from = i;
//							to = j;
//						}
//					}
//					return true;
//				}
//			}
			for(int i=0;i<n;i++){
				ker = g.getKernelGraph().getSuccessorsOf(i);
				env = g.getEnvelopGraph().getSuccessorsOf(i);
				if(env.neighborhoodSize()!=ker.neighborhoodSize()){
					for(int j=env.getFirstElement();j>=0;j=env.getNextElement()){
						if(i<j && !ker.contain(j)){
							int cost = dist[i][j];
							if(to==-1 || cost<minCost){
								minCost = cost;
								from = i;
								to = j;
							}
						}
					}
				}
			}
			if(from==-1){
				return false;
			}
			return true;
		}

		public boolean computeNextArcOld() {
			from = -1;
			to = -1;
			int minCost = 0;
			INeighbors env,ker;
			//new
			for(int i=0;i<n;i++){
				ker = g.getKernelGraph().getSuccessorsOf(i);
				env = g.getEnvelopGraph().getSuccessorsOf(i);
				if(ker.neighborhoodSize()<dMax[i]-1)
				if(ker.neighborhoodSize()==0){
					for(int j=env.getFirstElement();j>=0;j=env.getNextElement()){
						int cost = dist[i][j];
						if(g.getKernelGraph().getSuccessorsOf(j).neighborhoodSize()<dMax[j]-1)
						if(to==-1 || cost<minCost){
							minCost = cost;
							from = i;
							to = j;
						}
					}
//					if(to!=-1)
//					return true;
				}
			}
			if(to!=-1){
				return true;
			}
			for(int i=0;i<n;i++){
				ker = g.getKernelGraph().getSuccessorsOf(i);
				env = g.getEnvelopGraph().getSuccessorsOf(i);
				if(ker.neighborhoodSize()==0){
					for(int j=env.getFirstElement();j>=0;j=env.getNextElement()){
						int cost = dist[i][j];
						if(to==-1 || cost<minCost){
							minCost = cost;
							from = i;
							to = j;
						}
					}
//					return true;
				}
			}
			if(to!=-1){
				return true;
			}
			for(int i=0;i<n;i++){
				ker = g.getKernelGraph().getSuccessorsOf(i);
				env = g.getEnvelopGraph().getSuccessorsOf(i);
				if(env.neighborhoodSize()!=ker.neighborhoodSize()){
					for(int j=env.getFirstElement();j>=0;j=env.getNextElement()){
						if(i<j && !ker.contain(j)){
							int cost = dist[i][j];
							if(to==-1 || cost<minCost){
								minCost = cost;
								from = i;
								to = j;
							}
						}
					}
				}
			}
			if(from==-1){
				return false;
			}
			return true;
		}
	}

	private static class nextSol extends ArcStrategy<UndirectedGraphVar>{

		public nextSol (UndirectedGraphVar g){
			super(g);
		}

		@Override
		public boolean computeNextArc() {
			from = -1;
			to = -1;
			int minCost = 0;
			INeighbors env,ker;
			//new
			for(int i=0;i<n;i++){
				ker = g.getKernelGraph().getSuccessorsOf(i);
				env = g.getEnvelopGraph().getSuccessorsOf(i);
				if(ker.neighborhoodSize()==0){
					for(int j=env.getFirstElement();j>=0;j=env.getNextElement()){
						int cost = dist[i][j];
						if(g.getKernelGraph().getSuccessorsOf(j).neighborhoodSize()<dMax[j]-1)
						if(to==-1 || cost>minCost){
							minCost = cost;
							from = i;
							to = j;
						}
					}
					if(to!=-1)
					return true;
				}
			}
			for(int i=0;i<n;i++){
				ker = g.getKernelGraph().getSuccessorsOf(i);
				env = g.getEnvelopGraph().getSuccessorsOf(i);
				if(ker.neighborhoodSize()==0){
					for(int j=env.getFirstElement();j>=0;j=env.getNextElement()){
						int cost = dist[i][j];
						if(to==-1 || cost>minCost){
							minCost = cost;
							from = i;
							to = j;
						}
					}
					return true;
				}
			}
			for(int i=0;i<n;i++){
				ker = g.getKernelGraph().getSuccessorsOf(i);
				env = g.getEnvelopGraph().getSuccessorsOf(i);
				if(env.neighborhoodSize()!=ker.neighborhoodSize()){
					for(int j=env.getFirstElement();j>=0;j=env.getNextElement()){
						if(i<j && !ker.contain(j)){
							int cost = dist[i][j];
							if(to==-1 || cost>minCost){
								minCost = cost;
								from = i;
								to = j;
							}
						}
					}
				}
			}
			if(from==-1){
				return false;
			}
			return true;
		}
	}

	private static class viol extends ArcStrategy<UndirectedGraphVar>{

		public viol (UndirectedGraphVar g){
			super(g);
		}

		@Override
		public boolean computeNextArc() {
			from = -1;
			to = -1;
			INeighbors env,ker;
			//new
			int next = -1;
			int dM = 0;
			for(int i=0;i<n;i++){
				if(hk.getMST().getNeighborsOf(i).neighborhoodSize()-dMax[i]>dM){
					dM = hk.getMST().getNeighborsOf(i).neighborhoodSize()-dMax[i];
					next = i;
				}
			}
			if(next!=-1){
				int i = next;
				ker = g.getKernelGraph().getSuccessorsOf(i);
				env = g.getEnvelopGraph().getSuccessorsOf(i);
				if(ker.neighborhoodSize()!=env.neighborhoodSize()){
					for(int j=env.getFirstElement();j>=0;j=env.getNextElement()){
						if(!ker.contain(j)){
							from = i;
							to = j;
						}
					}
					return true;
				}
			}
			for(int i=0;i<n;i++){
				ker = g.getKernelGraph().getSuccessorsOf(i);
				env = g.getEnvelopGraph().getSuccessorsOf(i);
				if(ker.neighborhoodSize()!=env.neighborhoodSize()){
					for(int j=env.getFirstElement();j>=0;j=env.getNextElement()){
						if(!ker.contain(j)){
							from = i;
							to = j;
							return true;
						}
					}
				}
			}
			throw new UnsupportedOperationException();
		}
	}

	private static class Change extends AbstractStrategy<UndirectedGraphVar>{

		AbstractStrategy[] strats;
		public Change (UndirectedGraphVar g, AbstractStrategy... strats){
			super(new UndirectedGraphVar[]{g});
			this.strats = strats;
		}

		@Override
		public void init() {
			for(int i=0;i<strats.length;i++){
				strats[i].init();
			}
		}

		@Override
		public Decision getDecision() {
			if(solver.getMeasures().getSolutionCount()==0){
//				if(solver.getMeasures().getFailCount()<100){
					return strats[0].getDecision();
//				}
			}return strats[1].getDecision();
		}
	}

//		// GCC
//		int[] low = new int[n*2];
//		int[] up = new int[n*2];
//		int[][] costMatrix = new int[n*2][n*2];
//		for(int i=0;i<n;i++){
//			low[i] = up[i] = 1;
//			low[i+n] = 0;
//			up[i+n] = dMax[i]-1;
//			INeighbors nei = undi.getEnvelopGraph().getSuccessorsOf(i);
//			for(int j=nei.getFirstElement();j>=0;j=nei.getNextElement()){
//				costMatrix[i][j+n] = costMatrix[j+n][i] = costMatrix[i+n][j] = costMatrix[j][i+n] = dist[i][j];
//			}
//		}
//		low[0] = up[0] = 0;
//		low[n] = 1;
//		up[n] = dMax[0];
//		IntVar flow = VariableFactory.bounded("flowMax",n-1,n-1,solver);
//		gc.addPropagators(new PropGCC_LowUp_undirected(undi, flow, low, up, gc, solver));
//		// cost-GCC
//		gc.addPropagators(new PropGCC_cost_LowUp_undirected(undi, flow, totalCost,
//		costMatrix,low, up, gc, solver));
}