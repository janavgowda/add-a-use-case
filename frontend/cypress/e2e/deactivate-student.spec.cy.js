// UC-STU-deactivate-student: the deactivation in the Edit-student dialog. The student API calls are stubbed, so these
// tests check what the dialog shows and what it sends; the backend's own tests cover who may deactivate and what
// deactivation keeps. Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const john = {
  id: 4,
  username: 'j.smith@abc.edu',
  firstName: 'John',
  lastName: 'Smith',
  email: 'j.smith@abc.edu',
  enabled: true,
  roles: 'student',
  sectionId: 2,
  sectionName: '2023-2024',
  teamId: 1,
  teamName: 'Team1'
}

const page = (students) => ({
  flag: true,
  code: 200,
  message: 'Find students successfully',
  data: { content: students, number: 0, size: 50, totalElements: students.length }
})

const found = (student) => ({ flag: true, code: 200, message: 'Find student successfully', data: student })

const deactivated = (message) => ({
  flag: true,
  code: 200,
  message,
  data: { ...john, enabled: false }
})

describe('Deactivate a student', () => {
  beforeEach(() => {
    cy.login('b.gates@abc.edu', '123456')
    cy.intercept('POST', '**/students/4/deactivate', {
      delay: 1000, // Long enough to click Deactivate again while the request is in flight
      body: deactivated('Deactivate student successfully')
    }).as('deactivate')
  })

  function openStudent(student) {
    cy.intercept('POST', '**/students/search*', { body: page([student]) }).as('search')
    cy.intercept('GET', '**/students/4', { body: found(student) }).as('student')
    cy.visit('/students')
    cy.wait('@search')
    cy.get('.el-table__row').first().find('button').click() // Steps 1 and 2: open the student's details
    cy.wait('@student')
  }

  it('offers Deactivate and keeps the Status switch from turning an enabled student off (step 3)', () => {
    openStudent(john)

    cy.get('[data-cy=student-deactivate]').should('be.visible')
    cy.get('[data-cy=student-status]').should('have.class', 'is-disabled').click({ force: true })
    cy.get('[data-cy=student-status]').should('have.class', 'is-checked') // still Enabled
  })

  it('shows a deactivated student as Disabled and offers no deactivation (3a)', () => {
    openStudent({ ...john, enabled: false })

    cy.get('[data-cy=student-status]').should('not.have.class', 'is-checked').and('contain', 'Disabled')
    cy.get('[data-cy=student-deactivate]').should('not.exist')
    cy.get('[data-cy=student-status]').should('not.have.class', 'is-disabled') // The interim reactivation path
  })

  it('warns with every consequence, then deactivates with one request however often Deactivate is clicked (steps 4 to 6)', () => {
    openStudent(john)
    cy.get('[data-cy=student-deactivate]').click()

    cy.get('.el-message-box').should('contain', 'Deactivate John Smith?')
    cy.get('[data-cy=deactivate-consequences]')
      .should('contain', 'can no longer log in, and any session they have open ends')
      .and('contain', 'can no longer submit weekly activity reports or peer evaluations')
      .and('contain', 'teammates no longer evaluate them')
      .and('contain', 'team assignment, and submitted work are kept, and they can be reactivated later')
      .and('contain', 'This is not deletion.')

    cy.get('.deactivate-confirm').click()
    cy.get('.deactivate-confirm').should('be.disabled')
    cy.get('.deactivate-confirm').click({ force: true }) // A second click on the disabled button

    cy.wait('@deactivate')
    cy.get('.el-message--success').should('contain', 'John Smith has been deactivated.')
    cy.get('@deactivate.all').should('have.length', 1)
  })

  it('sends nothing and leaves the student enabled when cancelled at the warning (5a)', () => {
    openStudent(john)
    cy.get('[data-cy=student-deactivate]').click()

    cy.get('.deactivate-cancel').click()

    cy.get('.el-message-box').should('not.exist')
    cy.get('@deactivate.all').should('have.length', 0)
    cy.get('[data-cy=student-status]').should('have.class', 'is-checked')
    cy.get('[data-cy=student-deactivate]').should('be.visible') // The dialog stays open
  })

  it('says the student is already deactivated when someone else got there first (6a)', () => {
    cy.intercept('POST', '**/students/4/deactivate', { body: deactivated('Student is already deactivated') }).as(
      'already'
    )
    openStudent(john)
    cy.get('[data-cy=student-deactivate]').click()
    cy.get('.deactivate-confirm').click()

    cy.wait('@already')
    cy.get('.el-message--success').should('contain', 'John Smith is already deactivated.')
  })
})
