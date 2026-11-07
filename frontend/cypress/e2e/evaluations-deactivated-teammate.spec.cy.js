// UC-EVA-submit-evaluation with a deactivated teammate (BR-student-lifecycle; a consequence of UC-STU-deactivate-student).
// The team roster and the evaluator's stored evaluations are stubbed; the course section and its rubric come from the dev
// stack. The backend's own tests cover the server-side refusal. Run with `npm run cy:open`.

const teammate = (id, firstName, lastName, enabled) => ({
  id,
  username: `${firstName.toLowerCase()}@abc.edu`,
  firstName,
  lastName,
  email: `${firstName.toLowerCase()}@abc.edu`,
  enabled,
  roles: 'student',
  sectionId: 2,
  teamId: 1,
  teamName: 'Team1'
})

// Eric has been deactivated
const roster = {
  flag: true,
  code: 200,
  message: 'Find students by team id successfully',
  data: [teammate(4, 'John', 'Smith', true), teammate(5, 'Eric', 'Hudson', false), teammate(6, 'Jerry', 'Moon', true)]
}

const ratings = [1, 2, 3, 4, 5, 6].map((criterionId) => ({ criterionId, actualScore: 9 }))

const storedEvaluation = (evaluationId, evaluateeId, evaluateeName) => ({
  evaluationId,
  week: '2023-W32',
  evaluatorId: 4,
  evaluatorName: 'John Smith',
  evaluateeId,
  evaluateeName,
  ratings,
  totalScore: 54,
  publicComment: 'public',
  privateComment: 'private'
})

describe('Peer evaluation form with a deactivated teammate', () => {
  beforeEach(() => {
    cy.login('j.smith@abc.edu', '123456')
    cy.intercept('GET', '**/students/teams/*', { body: roster }).as('roster')
  })

  it('does not list a deactivated teammate on a new submission', () => {
    cy.intercept('GET', '**/evaluations/evaluators/*/week/*', {
      body: { flag: true, code: 200, message: 'Get peer evaluations by evaluator id and week successfully', data: [] }
    }).as('stored')
    cy.visit('/evaluations/submit-evaluations')
    cy.wait(['@roster', '@stored'])

    cy.get('.el-table__body').should('contain', 'John Smith').and('contain', 'Jerry Moon').and('not.contain', 'Eric Hudson')
  })

  it('leaves out an evaluation of a teammate deactivated since, and updates the others', () => {
    cy.intercept('GET', '**/evaluations/evaluators/*/week/*', {
      body: {
        flag: true,
        code: 200,
        message: 'Get peer evaluations by evaluator id and week successfully',
        data: [
          storedEvaluation(101, 4, 'John Smith'),
          storedEvaluation(102, 5, 'Eric Hudson'), // written before Eric's deactivation
          storedEvaluation(103, 6, 'Jerry Moon')
        ]
      }
    }).as('stored')
    cy.intercept('PUT', '**/evaluations/*', (req) => {
      req.reply({ flag: true, code: 200, message: 'Update peer evaluation successfully', data: req.body })
    }).as('update')
    cy.visit('/evaluations/submit-evaluations')
    cy.wait(['@roster', '@stored'])

    cy.get('.el-table__body').should('contain', 'John Smith').and('contain', 'Jerry Moon').and('not.contain', 'Eric Hudson')
    cy.contains('button', 'Update').click()

    cy.get('.el-message--success').should('contain', 'Peer evaluations updated successfully')
    cy.get('@update.all').should('have.length', 2)
    cy.get('@update.all').then((calls) => {
      const urls = calls.map((call) => call.request.url)
      expect(urls.some((url) => url.endsWith('/evaluations/102'))).to.equal(false)
    })
  })
})
