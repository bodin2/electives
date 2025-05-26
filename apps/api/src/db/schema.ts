import { relations } from 'drizzle-orm'
import { integer, primaryKey, sqliteTable, text } from 'drizzle-orm/sqlite-core'

// Students and electives are assigned to a team
export const teams = sqliteTable('teams', {
    id: integer('id').primaryKey(),
    name: text('name').notNull(),
})

/**
 * Electives are a collection of subjects that students can choose from.
 * Each elective can be associated with a team, allowing only students from that team to enroll in their electives' subjects.
 *
 * If an elective is deleted, all associated subjects will also be deleted.
 */
export const electives = sqliteTable('electives', {
    id: integer('id').primaryKey(),
    name: text('name').notNull(),
    /**
     * The team that this elective belongs to.
     * This is optional, meaning that an elective can exist without being associated with a team.
     * If a team is specified, only students from that team can enroll in the subjects of this elective.
     */
    teamId: integer('team_id').references(() => teams.id, { onDelete: 'restrict' }),
})

export const subjects = sqliteTable('subjects', {
    id: text('id').primaryKey(),
    /**
     * A subject must be associated with an elective. However, electives can exist without a team.
     */
    electiveId: integer('elective_id').references(() => electives.id, { onDelete: 'cascade' }),

    name: text('name').notNull(),
    description: text('description').notNull(),
    location: text('location').notNull(),

    maxStudents: integer('max_students').notNull(),
})

export const students = sqliteTable('students', {
    id: integer('id').primaryKey(),

    firstName: text('first_name').notNull(),
    middleName: text('middle_name'),
    lastName: text('last_name').notNull(),

    // A student must have a team
    teamId: integer('team_id')
        .notNull()
        .references(() => teams.id, { onDelete: 'restrict' }),

    /**
     * Hash of the student's password.
     */
    hash: text('hash').notNull(),

    /**
     * Hash of the session ID.
     */
    sessionHash: text('session_hash'),
})

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

export const studentsRelations = relations(students, ({ many }) => ({
    subjects: many(studentsToSubjects),
}))

export const subjectsRelations = relations(subjects, ({ many }) => ({
    students: many(studentsToSubjects),
}))
