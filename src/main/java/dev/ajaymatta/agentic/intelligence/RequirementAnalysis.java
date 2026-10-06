package dev.ajaymatta.agentic.intelligence;

import java.util.List;

public record RequirementAnalysis(String normalizedProblem, List<Criterion> criteria,
                                  List<Question> questions, List<String> assumptions,
                                  List<String> risks, List<String> constraints) {
    public RequirementAnalysis {
        criteria = List.copyOf(criteria);
        questions = List.copyOf(questions);
        assumptions = List.copyOf(assumptions);
        risks = List.copyOf(risks);
        constraints = List.copyOf(constraints);
    }
    public boolean resolved() { return questions.isEmpty() && !criteria.isEmpty(); }
    public record Criterion(String id, String capability, String description, boolean behavioral) {}
    public record Question(String id, String category, String question) {}
}
