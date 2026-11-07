<template>
  <el-form
    ref="studentForm"
    :model="studentBeingEdited"
    :rules="rules"
    label-width="110px"
    style="padding-right: 30px"
    label-position="left"
  >
    <el-form-item label="Id:">
      <el-input v-model="studentBeingEdited.id" disabled></el-input>
    </el-form-item>
    <el-form-item label="Username:" prop="username">
      <el-input v-model="studentBeingEdited.username"></el-input>
    </el-form-item>
    <el-form-item label="Status:" prop="enabled">
      <!-- Enable-only: an enabled student is deactivated through the Deactivate button and its confirmation
           (UC-STU-deactivate-student); a deactivated one may be switched back on (UC-STU-reactivate-student, interim path) -->
      <el-switch
        v-model="studentBeingEdited.enabled"
        :disabled="loadedEnabled"
        inline-prompt
        active-text="Enabled"
        inactive-text="Disabled"
        style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
        data-cy="student-status"
      />
      <el-button
        v-if="loadedEnabled"
        type="danger"
        plain
        size="small"
        style="margin-left: 12px"
        data-cy="student-deactivate"
        @click="confirmDeactivation()"
      >
        Deactivate
      </el-button>
    </el-form-item>
    <el-form-item label="First Name:" prop="firstName">
      <el-input v-model="studentBeingEdited.firstName"></el-input>
    </el-form-item>
    <el-form-item label="Last Name:" prop="lastName">
      <el-input v-model="studentBeingEdited.lastName"></el-input>
    </el-form-item>
    <el-form-item label="Email:" prop="email">
      <el-input v-model="studentBeingEdited.email"></el-input>
    </el-form-item>
    <el-form-item label="Team:" prop="teamName">
      <el-input v-model="studentBeingEdited.teamName" disabled></el-input>
    </el-form-item>
    <div style="display: flex; justify-content: end">
      <el-button @click="$emit('close-dialog')"> Cancel </el-button>
      <el-button type="primary" @click="updateExistingStudent()"> Confirm </el-button>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { DeactivateStudentResponse, Student } from '@/apis/student/types'
import type { FormInstance } from 'element-plus'
import { deactivateStudent, findStudentById, updateStudent } from '@/apis/student'
import { h, ref, onMounted } from 'vue'

const { studentId } = defineProps(['studentId'])
const emit = defineEmits(['close-dialog'])

// Note that the structure of the userData is NOT the same as the User interface since the roles are stored as an array
const studentBeingEdited = ref<Student>({
  // Data of the user being added or edited
  id: NaN,
  username: '',
  enabled: false,
  firstName: '',
  lastName: '',
  email: ''
})

// The status as loaded, not as the switch currently shows it: it decides whether Deactivate is offered (step 3, 3a)
const loadedEnabled = ref<boolean>(false)

const studentForm = ref<FormInstance>() // Form reference

// Validation rules
const rules = {
  username: [
    { required: true, message: 'Please input your email as your username', trigger: 'blur' },
    {
      pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
      message: 'Enter your email as username.',
      trigger: 'blur'
    }
  ],
  enabled: [{ required: true, message: 'Please select the status of the user.', trigger: 'blur' }],
  firstName: [
    { required: true, message: 'Please provide the first name of the user.', trigger: 'blur' }
  ],
  lastName: [
    { required: true, message: 'Please provide the last name of the user.', trigger: 'blur' }
  ],
  email: [
    { required: true, message: 'Please provide the email of the user.', trigger: 'blur' },
    { type: 'email', message: 'Please provide a valid email.', trigger: 'blur' }
  ]
}

async function loadStudent(studentId: number) {
  // Load the user to be edited
  const result = await findStudentById(studentId)

  studentBeingEdited.value.id = result.data.id as number
  studentBeingEdited.value.username = result.data.username
  studentBeingEdited.value.enabled = result.data.enabled
  studentBeingEdited.value.firstName = result.data.firstName
  studentBeingEdited.value.lastName = result.data.lastName
  studentBeingEdited.value.email = result.data.email
  studentBeingEdited.value.teamName = result.data.teamName
  loadedEnabled.value = result.data.enabled === true
}

// The consequences in UC-STU-deactivate-student's Associated Information, shown at step 4
const DEACTIVATION_CONSEQUENCES = [
  'They can no longer log in, and any session they have open ends.',
  'They can no longer submit weekly activity reports or peer evaluations.',
  'Their teammates no longer evaluate them.',
  'Their account, team assignment, and submitted work are kept, and they can be reactivated later.',
  'This is not deletion.'
]

const ALREADY_DEACTIVATED_MESSAGE = 'Student is already deactivated'

async function confirmDeactivation() {
  const name = `${studentBeingEdited.value.firstName} ${studentBeingEdited.value.lastName}`
  let result: DeactivateStudentResponse | undefined

  try {
    // Steps 4 and 5: warn, list the consequences, and ask for confirmation
    await ElMessageBox.confirm(
      h('div', { 'data-cy': 'deactivate-consequences' }, [
        h('p', 'Deactivating this student has these consequences:'),
        h(
          'ul',
          DEACTIVATION_CONSEQUENCES.map((consequence) => h('li', consequence))
        )
      ]),
      `Deactivate ${name}?`,
      {
        type: 'warning',
        confirmButtonText: 'Deactivate',
        cancelButtonText: 'Cancel',
        confirmButtonClass: 'el-button--danger deactivate-confirm',
        cancelButtonClass: 'deactivate-cancel',
        beforeClose: async (action, instance, done) => {
          if (action !== 'confirm') {
            done()
            return
          }
          instance.confirmButtonLoading = true // A loading button is disabled, so a second click sends nothing
          try {
            result = await deactivateStudent(studentBeingEdited.value.id as number)
          } catch {
            // The shared request instance has already reported the error
          } finally {
            instance.confirmButtonLoading = false
            done()
          }
        }
      }
    )
  } catch {
    return // 5a: cancelled at the warning; nothing was sent and the dialog stays open
  }

  if (!result) {
    return
  }
  // Step 6, or 6a when another course admin or instructor deactivated them first
  const deactivatedName = `${result.data.firstName} ${result.data.lastName}`
  if (result.message === ALREADY_DEACTIVATED_MESSAGE) {
    ElMessage.success(`${deactivatedName} is already deactivated.`)
  } else {
    ElMessage.success(`${deactivatedName} has been deactivated.`)
  }
  emit('close-dialog')
}

onMounted(() => {
  loadStudent(studentId) // Load the artifact to be edited
})

async function updateExistingStudent() {
  await studentForm.value!.validate() // Validate the form

  const updatedStudent: Student = {
    id: studentBeingEdited.value.id,
    username: studentBeingEdited.value.username,
    enabled: studentBeingEdited.value.enabled,
    firstName: studentBeingEdited.value.firstName,
    lastName: studentBeingEdited.value.lastName,
    email: studentBeingEdited.value.email
  }
  console.log(updatedStudent)
  // Call the API to update the user
  await updateStudent(updatedStudent)

  ElMessage.success('Student updated successfully')
  emit('close-dialog')
}
</script>

<style scoped lang="scss"></style>
