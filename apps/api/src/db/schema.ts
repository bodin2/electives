import { relations } from 'drizzle-orm'
import { blob, integer, primaryKey, sqliteTable, text } from 'drizzle-orm/sqlite-core'

// Students and electives are assigned to a team
export const teams = sqliteTable('teams', {
    id: integer('id').notNull().primaryKey(),
    name: text('name').notNull(),
})

/**
 * Electives are a collection of subjects that students can choose from.
 * Each elective can be associated with a team, allowing only students from that team to enroll in their electives' subjects.
 *
 * If an elective is deleted, all associated subjects will also be deleted.
 */
export const electives = sqliteTable('electives', {
    id: integer('id').notNull().primaryKey(),
    name: text('name').notNull(),
    /**
     * The team that this elective belongs to. An elective can exist without being associated with a team.
     *
     * If a team is specified, only students in that team can enroll in subjects of this elective.
     */
    teamId: integer('team_id').references(() => teams.id),

    startDate: integer('start_date', { mode: 'timestamp' }),
    endDate: integer('end_date', { mode: 'timestamp' }),
})

export const subjects = sqliteTable('subjects', {
    id: text('id').notNull().primaryKey(),

    /**
     * A subject must be associated with an elective. However, electives can exist without a team.
     */
    electiveId: integer('elective_id')
        .notNull()
        .references(() => electives.id),
    /**
     * The team that this subject belongs to. A subject can exist without being associated with a team.
     *
     * If a team is specified, only students in that team can enroll in this subject.
     * **This does not override the elective's team association, but only restricts it further.**
     *
     * Here is a valid use case:
     *
     * ### Scenario
     *
     * - You have an elective for the team `2568_3_0` (M3 Y2568). This subject belongs to this elective.
     * - You want to restrict this subject, only to those who also belong to the `2568_3_1` (M3 Y2568 English Program) team.
     *
     * ### Implementation
     *
     * - You set the subject's elective's `teamId` to `2568_3_0`.
     * - You set this subject's `teamId` to `2568_3_1`.
     */
    teamId: integer('team_id').references(() => teams.id),

    name: text('name').notNull(),
    description: text('description').notNull(),
    /**
     * Tag of the subject, such as SubjectTag.MATH, etc.
     */
    tag: integer('tag').notNull(),
    location: text('location').notNull(),

    maxStudents: integer('max_students').notNull(),
})

export const users = sqliteTable('users', {
    id: integer('id').notNull().primaryKey(),

    firstName: text('first_name').notNull(),
    middleName: text('middle_name'),
    lastName: text('last_name').notNull(),

    /**
     * Hash of the user's password.
     */
    hash: text('hash').notNull(),
    /**
     * Hash of the session ID.
     */
    sessionHash: text('session_hash'),
})

export const students = sqliteTable('students', {
    id: integer('id').notNull().primaryKey(),
})

export const teachers = sqliteTable('teachers', {
    id: integer('id').notNull().primaryKey(),
    avatar: blob('avatar').$type<Uint8Array>(),
})

/// RELATIONS

export const usersRelations = relations(users, ({ one }) => ({
    student: one(students, {
        fields: [users.id],
        references: [students.id],
    }),
    teacher: one(teachers, {
        fields: [users.id],
        references: [teachers.id],
    }),
}))

export const teachersRelations = relations(teachers, ({ one, many }) => ({
    user: one(users, {
        fields: [teachers.id],
        references: [users.id],
    }),
    subjects: many(teachersToSubjects),
}))

export const studentsRelations = relations(students, ({ one, many }) => ({
    user: one(users, {
        fields: [students.id],
        references: [users.id],
    }),
    subjects: many(studentsToSubjects),
    teams: many(studentsToTeams),
}))

export const teamsRelations = relations(teams, ({ many }) => ({
    students: many(studentsToTeams),
    electives: many(electives),
}))

export const subjectsRelations = relations(subjects, ({ one, many }) => ({
    students: many(studentsToSubjects),
}))

/// JOINS

export const studentsToTeams = sqliteTable(
    'students_to_teams',
    {
        studentId: integer('student_id')
            .notNull()
            .references(() => students.id),
        teamId: integer('team_id')
            .notNull()
            .references(() => teams.id),
    },
    t => [primaryKey({ columns: [t.studentId, t.teamId] })],
)

export const studentsToTeamsRelations = relations(studentsToTeams, ({ one }) => ({
    student: one(students, {
        fields: [studentsToTeams.studentId],
        references: [students.id],
    }),
    team: one(teams, {
        fields: [studentsToTeams.teamId],
        references: [teams.id],
    }),
}))

export const studentsToSubjects = sqliteTable(
    'students_to_subjects',
    {
        studentId: integer('student_id')
            .notNull()
            .references(() => students.id),
        subjectId: text('subject_id')
            .notNull()
            .references(() => subjects.id),
    },
    t => [primaryKey({ columns: [t.studentId, t.subjectId] })],
)
export const studentsToSubjectsRelations = relations(studentsToSubjects, ({ one }) => ({
    subject: one(subjects, {
        fields: [studentsToSubjects.subjectId],
        references: [subjects.id],
    }),
    student: one(students, {
        fields: [studentsToSubjects.studentId],
        references: [students.id],
    }),
}))

export const teachersToSubjects = sqliteTable(
    'teachers_to_subjects',
    {
        teacherId: integer('teacher_id')
            .notNull()
            .references(() => teachers.id),
        subjectId: text('subject_id')
            .notNull()
            .references(() => subjects.id),
    },
    t => [primaryKey({ columns: [t.teacherId, t.subjectId] })],
)

export const teachersToSubjectsRelations = relations(teachersToSubjects, ({ one }) => ({
    subject: one(subjects, {
        fields: [teachersToSubjects.subjectId],
        references: [subjects.id],
    }),
    teacher: one(teachers, {
        fields: [teachersToSubjects.teacherId],
        references: [teachers.id],
    }),
}))
