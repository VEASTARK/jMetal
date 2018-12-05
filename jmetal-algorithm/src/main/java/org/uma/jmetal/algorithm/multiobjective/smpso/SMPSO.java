package org.uma.jmetal.algorithm.multiobjective.smpso;

import org.uma.jmetal.algorithm.impl.AbstractParticleSwarmOptimization;
import org.uma.jmetal.measure.Measurable;
import org.uma.jmetal.measure.MeasureManager;
import org.uma.jmetal.measure.impl.BasicMeasure;
import org.uma.jmetal.measure.impl.SimpleMeasureManager;
import org.uma.jmetal.operator.MutationOperator;
import org.uma.jmetal.problem.DoubleProblem;
import org.uma.jmetal.solution.DoubleSolution;
import org.uma.jmetal.util.archive.BoundedArchive;
import org.uma.jmetal.util.comparator.DominanceComparator;
import org.uma.jmetal.util.evaluator.SolutionListEvaluator;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;
import org.uma.jmetal.util.solutionattribute.impl.GenericSolutionAttribute;
import org.uma.jmetal.util.terminationcondition.TerminationCondition;

import java.util.*;

/**
 * This class implements the SMPSO algorithm described in:
 * SMPSO: A new PSO-based metaheuristic for multi-objective optimization
 * MCDM 2009. DOI: http://dx.doi.org/10.1109/MCDM.2009.4938830
 *
 * @author Antonio J. Nebro <antonio@lcc.uma.es>
 */
@SuppressWarnings("serial")
public class SMPSO extends AbstractParticleSwarmOptimization<DoubleSolution, List<DoubleSolution>> implements Measurable {
  private DoubleProblem problem;

  private double c1Max;
  private double c1Min;
  private double c2Max;
  private double c2Min;
  private double r1Max;
  private double r1Min;
  private double r2Max;
  private double r2Min;
  private double changeVelocity1;
  private double changeVelocity2;

  private double inertiaWeight ;

  private int swarmSize;
  private int evaluations;

  private GenericSolutionAttribute<DoubleSolution, DoubleSolution> localBest;
  private double[][] speed;

  private JMetalRandom randomGenerator;

  private BoundedArchive<DoubleSolution> leaders;
  private Comparator<DoubleSolution> dominanceComparator;

  private MutationOperator<DoubleSolution> mutation;

  private double deltaMax[];
  private double deltaMin[];

  private SolutionListEvaluator<DoubleSolution> evaluator;

  protected SimpleMeasureManager measureManager ;
  protected BasicMeasure<Map<String, Object>> algorithmDataMeasure ;
  protected Map<String, Object> algorithmStatusData ;
  protected long initComputingTime ;

  private TerminationCondition terminationCondition ;
  /**
   * Constructor
   */
  public SMPSO(DoubleProblem problem, int swarmSize, TerminationCondition terminationCondition,
               BoundedArchive<DoubleSolution> leaders,
               MutationOperator<DoubleSolution> mutationOperator, double r1Min, double r1Max,
               double r2Min, double r2Max, double c1Min, double c1Max, double c2Min, double c2Max,
               double inertiaWeight, double changeVelocity1, double changeVelocity2,
               SolutionListEvaluator<DoubleSolution> evaluator) {
    this.problem = problem;
    this.swarmSize = swarmSize;
    this.leaders = leaders;
    this.mutation = mutationOperator;

    this.terminationCondition = terminationCondition ;

    this.r1Max = r1Max;
    this.r1Min = r1Min;
    this.r2Max = r2Max;
    this.r2Min = r2Min;
    this.c1Max = c1Max;
    this.c1Min = c1Min;
    this.c2Max = c2Max;
    this.c2Min = c2Min;
    this.changeVelocity1 = changeVelocity1;
    this.changeVelocity2 = changeVelocity2;
    this.inertiaWeight = inertiaWeight ;

    randomGenerator = JMetalRandom.getInstance();
    this.evaluator = evaluator;

    dominanceComparator = new DominanceComparator<DoubleSolution>();
    localBest = new GenericSolutionAttribute<DoubleSolution, DoubleSolution>();
    speed = new double[swarmSize][problem.getNumberOfVariables()];

    deltaMax = new double[problem.getNumberOfVariables()];
    deltaMin = new double[problem.getNumberOfVariables()];
    for (int i = 0; i < problem.getNumberOfVariables(); i++) {
      deltaMax[i] = (problem.getUpperBound(i) - problem.getLowerBound(i)) / 2.0;
      deltaMin[i] = -deltaMax[i];
    }

    algorithmStatusData = new HashMap<String, Object>();
    algorithmDataMeasure = new BasicMeasure<>() ;
    measureManager = new SimpleMeasureManager() ;
    measureManager.setPushMeasure("ALGORITHM_DATA", algorithmDataMeasure);
  }

  protected void updateLeadersDensityEstimator() {
    leaders.computeDensityEstimator();
  }

  @Override
  protected void initProgress() {
    evaluations = swarmSize;
    updateLeadersDensityEstimator();

    updateStatusData();
  }

  @Override
  protected void updateProgress() {
    evaluations += swarmSize ;
    updateLeadersDensityEstimator();

    updateStatusData();
  }

  @Override
  public void run() {
    initComputingTime = System.currentTimeMillis() ;
    super.run();
  }

  private void updateStatusData() {
    algorithmStatusData.put("EVALUATIONS", evaluations) ;
    algorithmStatusData.put("POPULATION", leaders.getSolutionList()) ;
    algorithmStatusData.put("COMPUTING_TIME", System.currentTimeMillis() - initComputingTime) ;

    algorithmDataMeasure.push(algorithmStatusData);
  }

  @Override
  protected boolean isStoppingConditionReached() {
    return terminationCondition.check(algorithmStatusData);
  }

  @Override
  protected List<DoubleSolution> createInitialSwarm() {
    List<DoubleSolution> swarm = new ArrayList<>(swarmSize);

    DoubleSolution newSolution;
    for (int i = 0; i < swarmSize; i++) {
      newSolution = problem.createSolution();
      swarm.add(newSolution);
    }

    return swarm;
  }

  @Override
  protected List<DoubleSolution> evaluateSwarm(List<DoubleSolution> swarm) {
    swarm = evaluator.evaluate(swarm, problem);

    return swarm;
  }

  @Override
  protected void initializeLeader(List<DoubleSolution> swarm) {
    for (DoubleSolution particle : swarm) {
      leaders.add(particle);
    }
  }

  @Override
  protected void initializeVelocity(List<DoubleSolution> swarm) {
    for (int i = 0; i < swarm.size(); i++) {
      for (int j = 0; j < problem.getNumberOfVariables(); j++) {
        speed[i][j] = 0.0;
      }
    }
  }

  @Override
  protected void initializeParticlesMemory(List<DoubleSolution> swarm) {
    for (DoubleSolution particle : swarm) {
      localBest.setAttribute(particle, (DoubleSolution) particle.copy());
    }
  }

  @Override
  protected void updateVelocity(List<DoubleSolution> swarm) {
    double r1, r2, c1, c2;
    DoubleSolution bestGlobal;

    for (int i = 0; i < swarm.size(); i++) {
      DoubleSolution particle = (DoubleSolution) swarm.get(i).copy();
      DoubleSolution bestParticle = (DoubleSolution) localBest.getAttribute(swarm.get(i)).copy();

      bestGlobal = selectGlobalBest();

      r1 = randomGenerator.nextDouble(r1Min, r1Max);
      r2 = randomGenerator.nextDouble(r2Min, r2Max);
      c1 = randomGenerator.nextDouble(c1Min, c1Max);
      c2 = randomGenerator.nextDouble(c2Min, c2Max);

      for (int var = 0; var < particle.getNumberOfVariables(); var++) {
        speed[i][var] = velocityConstriction(constrictionCoefficient(c1, c2) * (
                       inertiaWeight* speed[i][var] +
                                c1 * r1 * (bestParticle.getVariableValue(var) - particle.getVariableValue(var)) +
                                c2 * r2 * (bestGlobal.getVariableValue(var) - particle.getVariableValue(var))),
                deltaMax, deltaMin, var);
      }
    }
  }

  @Override
  protected void updatePosition(List<DoubleSolution> swarm) {
    for (int i = 0; i < swarmSize; i++) {
      DoubleSolution particle = swarm.get(i);
      for (int j = 0; j < particle.getNumberOfVariables(); j++) {
        particle.setVariableValue(j, particle.getVariableValue(j) + speed[i][j]);

        if (particle.getVariableValue(j) < problem.getLowerBound(j)) {
          particle.setVariableValue(j, problem.getLowerBound(j));
          speed[i][j] = speed[i][j] * changeVelocity1;
        }
        if (particle.getVariableValue(j) > problem.getUpperBound(j)) {
          particle.setVariableValue(j, problem.getUpperBound(j));
          speed[i][j] = speed[i][j] * changeVelocity2;
        }
      }
    }
  }

  @Override
  protected void perturbation(List<DoubleSolution> swarm) {
    for (int i = 0; i < swarm.size(); i++) {
      if ((i % 6) == 0) {
        mutation.execute(swarm.get(i));
      }
    }
  }

  @Override
  protected void updateLeaders(List<DoubleSolution> swarm) {
    for (DoubleSolution particle : swarm) {
      leaders.add((DoubleSolution) particle.copy());
    }
  }

  @Override
  protected void updateParticlesMemory(List<DoubleSolution> swarm) {
    for (int i = 0; i < swarm.size(); i++) {
      int flag = dominanceComparator.compare(swarm.get(i), localBest.getAttribute(swarm.get(i)));
      if (flag != 1) {
        DoubleSolution particle = (DoubleSolution) swarm.get(i).copy();
        localBest.setAttribute(swarm.get(i), particle);
      }
    }
  }

  @Override
  public List<DoubleSolution> getResult() {
    return leaders.getSolutionList();
  }

  protected DoubleSolution selectGlobalBest() {
    DoubleSolution one, two;
    DoubleSolution bestGlobal;
    int pos1 = randomGenerator.nextInt(0, leaders.getSolutionList().size() - 1);
    int pos2 = randomGenerator.nextInt(0, leaders.getSolutionList().size() - 1);
    one = leaders.getSolutionList().get(pos1);
    two = leaders.getSolutionList().get(pos2);

    if (leaders.getComparator().compare(one, two) < 1) {
      bestGlobal = (DoubleSolution) one.copy();
    } else {
      bestGlobal = (DoubleSolution) two.copy();
    }

    return bestGlobal;
  }

  private double velocityConstriction(double v, double[] deltaMax, double[] deltaMin,
                                      int variableIndex) {
    double result;

    double dmax = deltaMax[variableIndex];
    double dmin = deltaMin[variableIndex];

    result = v;

    if (v > dmax) {
      result = dmax;
    }

    if (v < dmin) {
      result = dmin;
    }

    return result;
  }

  protected double constrictionCoefficient(double c1, double c2) {
    double rho = c1 + c2;
    if (rho <= 4) {
      return 1.0;
    } else {
      return 2 / (2 - rho - Math.sqrt(Math.pow(rho, 2.0) - 4.0 * rho));
    }
  }

  @Override
  public String getName() {
    return "SMPSO";
  }

  @Override
  public String getDescription() {
    return "Speed contrained Multiobjective PSO";
  }

  /* Getters */
  public int getSwarmSize() {
    return swarmSize;
  }

  public int getEvaluations() {
    return evaluations;
  }

  @Override
  public MeasureManager getMeasureManager() {
    return measureManager;
  }
}
